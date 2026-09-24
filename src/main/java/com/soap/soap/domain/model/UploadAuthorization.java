package com.soap.soap.domain.model;

import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public record UploadAuthorization(
    URI uploadUrl, String httpMethod, Map<String, String> requiredHeaders, Instant expiresAt) {

  public UploadAuthorization {
    Objects.requireNonNull(uploadUrl, "uploadUrl must not be null");
    Objects.requireNonNull(httpMethod, "httpMethod must not be null");
    Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    requiredHeaders =
        requiredHeaders == null
            ? Collections.emptyMap()
            : Collections.unmodifiableMap(requiredHeaders);
  }
}
