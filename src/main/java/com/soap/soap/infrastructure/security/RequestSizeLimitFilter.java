package com.soap.soap.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestSizeLimitFilter extends OncePerRequestFilter {
  private final int maximumBytes;

  public RequestSizeLimitFilter(
      @Value("${app.security.max-request-bytes:1200000}") int maximumBytes) {
    this.maximumBytes = maximumBytes;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !"POST".equalsIgnoreCase(request.getMethod())
        || !request.getRequestURI().startsWith("/ws");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (request.getContentLengthLong() > maximumBytes) {
      reject(response);
      return;
    }
    var bytes = request.getInputStream().readNBytes(maximumBytes + 1);
    if (bytes.length > maximumBytes) {
      reject(response);
      return;
    }
    filterChain.doFilter(new BufferedRequest(request, bytes), response);
  }

  private void reject(HttpServletResponse response) throws IOException {
    // sendError triggers a container ERROR dispatch to /error, where Spring Security may replace
    // this status with 403. Commit a plain transport-level response instead: SOAP never sees it.
    response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
    response.setContentType("text/plain");
    response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
    response.getWriter().write("Request too large");
    response.flushBuffer();
  }

  private static final class BufferedRequest extends HttpServletRequestWrapper {
    private final byte[] body;

    private BufferedRequest(HttpServletRequest request, byte[] body) {
      super(request);
      this.body = body.clone();
    }

    @Override
    public ServletInputStream getInputStream() {
      var input = new ByteArrayInputStream(body);
      return new ServletInputStream() {
        @Override
        public int read() {
          return input.read();
        }

        @Override
        public boolean isFinished() {
          return input.available() == 0;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
          throw new UnsupportedOperationException("Async reads are not supported");
        }
      };
    }
  }
}
