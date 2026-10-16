# -----------------------------------------------------------------------------
# Phase 17.5H: CloudFront Distribution for Angular Frontend & EC2 Backend
# -----------------------------------------------------------------------------

# 1. CloudFront Origin Access Control (OAC) for private S3 frontend bucket
resource "aws_cloudfront_origin_access_control" "frontend" {
  name                              = "${local.name_prefix}-frontend-oac"
  description                       = "OAC for private Angular frontend S3 bucket"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# 1b. CloudFront Origin Access Control (OAC) for private Platform Media S3 bucket
resource "aws_cloudfront_origin_access_control" "platform_media" {
  name                              = "${local.name_prefix}-platform-media-oac"
  description                       = "OAC for private Platform Media S3 bucket"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# 2. CloudFront Function for Angular SPA client-side route rewrites
# Scoped ONLY to the frontend S3 default cache behavior (never touches /api/* or /ws*)
resource "aws_cloudfront_function" "spa_rewrite" {
  name    = "${local.name_prefix}-spa-rewrite"
  runtime = "cloudfront-js-2.0"
  comment = "Angular SPA navigation rewrite to /index.html without affecting API routes"
  publish = true
  code    = <<-EOT
function handler(event) {
    var request = event.request;
    var uri = request.uri;

    // Preserve root, index.html, and any URI with a file extension (e.g. .js, .css, .ico, .png, .svg)
    if (uri === '/' || uri === '/index.html' || uri.indexOf('.') !== -1) {
        return request;
    }

    // Rewrite client-side navigation paths (e.g. /login, /library, /reader/view) to /index.html
    request.uri = '/index.html';
    return request;
}
EOT
}

# 2b. CloudFront Function to rewrite /media/* requests by stripping the /media prefix
# Enables clients to request /media/covers/CO/x.webp while S3 stores covers/CO/x.webp
resource "aws_cloudfront_function" "media_rewrite" {
  name    = "${local.name_prefix}-media-rewrite"
  runtime = "cloudfront-js-2.0"
  comment = "Strip /media prefix from request URI for Platform Media S3 origin"
  publish = true
  code    = <<-EOT
function handler(event) {
    var request = event.request;
    request.uri = request.uri.replace(/^\/media/, '');
    return request;
}
EOT
}

# 3. AWS Managed Cache Policies
data "aws_cloudfront_cache_policy" "caching_optimized" {
  name = "Managed-CachingOptimized"
}

data "aws_cloudfront_cache_policy" "caching_disabled" {
  name = "Managed-CachingDisabled"
}

# 4. Custom Origin Request Policy for Backend EC2 (REST API & SOAP)
resource "aws_cloudfront_origin_request_policy" "backend_api" {
  name    = "${local.name_prefix}-backend-api-orp"
  comment = "Forward required headers, query strings, and body for Spring Boot REST API and SOAP"

  cookies_config {
    cookie_behavior = "none"
  }

  headers_config {
    header_behavior = "whitelist"
    headers {
      items = [
        "Accept",
        "Access-Control-Request-Headers",
        "Access-Control-Request-Method",
        "Authorization",
        "Content-Type",
        "Origin",
        "SOAPAction",
        "X-Correlation-ID"
      ]
    }
  }

  query_strings_config {
    query_string_behavior = "all"
  }
}

# 5. CloudFront Standard Distribution
resource "aws_cloudfront_distribution" "main" {
  enabled             = true
  is_ipv6_enabled     = true
  comment             = "${var.project_name} ${var.environment} public test distribution"
  default_root_object = "index.html"
  price_class         = "PriceClass_100"

  # Origin A: Private Angular Frontend S3 Bucket (via OAC)
  origin {
    domain_name              = aws_s3_bucket.frontend.bucket_regional_domain_name
    origin_id                = "S3-frontend"
    origin_access_control_id = aws_cloudfront_origin_access_control.frontend.id
  }

  # Origin B: EC2 Single Host Runtime (Caddy HTTP Port 80 via Elastic IP DNS)
  origin {
    domain_name = aws_eip.backend.public_dns
    origin_id   = "EC2-backend"

    custom_origin_config {
      http_port                = 80
      https_port               = 443
      origin_protocol_policy   = "http-only"
      origin_ssl_protocols     = ["TLSv1.2"]
      origin_keepalive_timeout = 5
      origin_read_timeout      = 30
    }
  }

  # Origin C: Private Platform Media S3 Bucket (via OAC)
  origin {
    domain_name              = aws_s3_bucket.platform_media.bucket_regional_domain_name
    origin_id                = "S3-platform-media"
    origin_access_control_id = aws_cloudfront_origin_access_control.platform_media.id
  }

  # ---------------------------------------------------------------------------
  # Default Cache Behavior: Angular Frontend (S3)
  # ---------------------------------------------------------------------------
  default_cache_behavior {
    target_origin_id       = "S3-frontend"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    cache_policy_id        = data.aws_cloudfront_cache_policy.caching_optimized.id
    compress               = true

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.spa_rewrite.arn
    }
  }

  # ---------------------------------------------------------------------------
  # Ordered Cache Behavior 0: Platform Media Assets (/media/*)
  # ---------------------------------------------------------------------------
  ordered_cache_behavior {
    path_pattern           = "/media/*"
    target_origin_id       = "S3-platform-media"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    cache_policy_id        = data.aws_cloudfront_cache_policy.caching_optimized.id
    compress               = true

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.media_rewrite.arn
    }
  }

  # ---------------------------------------------------------------------------
  # Ordered Cache Behavior 1: Spring Boot REST API (/api/*)
  # ---------------------------------------------------------------------------
  ordered_cache_behavior {
    path_pattern             = "/api/*"
    target_origin_id         = "EC2-backend"
    viewer_protocol_policy   = "redirect-to-https"
    allowed_methods          = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
    cached_methods           = ["GET", "HEAD", "OPTIONS"]
    cache_policy_id          = data.aws_cloudfront_cache_policy.caching_disabled.id
    origin_request_policy_id = aws_cloudfront_origin_request_policy.backend_api.id
    compress                 = true
  }

  # ---------------------------------------------------------------------------
  # Ordered Cache Behavior 2: SOAP Web Services (/ws*)
  # ---------------------------------------------------------------------------
  ordered_cache_behavior {
    path_pattern             = "/ws*"
    target_origin_id         = "EC2-backend"
    viewer_protocol_policy   = "redirect-to-https"
    allowed_methods          = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
    cached_methods           = ["GET", "HEAD", "OPTIONS"]
    cache_policy_id          = data.aws_cloudfront_cache_policy.caching_disabled.id
    origin_request_policy_id = aws_cloudfront_origin_request_policy.backend_api.id
    compress                 = true
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }

  tags = merge(local.common_tags, {
    Name = "${local.name_prefix}-cloudfront"
  })
}
