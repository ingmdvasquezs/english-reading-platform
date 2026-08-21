package com.soap.soap.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class JwtAuthenticationFilterTest {
  private final JwtDecoder decoder = mock(JwtDecoder.class);
  private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(decoder);

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void allowsPublicSoapRequestsWithoutAToken() throws Exception {
    var chain = mock(jakarta.servlet.FilterChain.class);
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();
    filter.doFilter(request, response, chain);
    verify(chain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  void authenticatesAValidToken() throws Exception {
    var chain = mock(jakarta.servlet.FilterChain.class);
    var request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer valid-token");
    var response = new MockHttpServletResponse();
    var jwt =
        new Jwt(
            "valid-token",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("alg", "HS256"),
            Map.of("sub", java.util.UUID.randomUUID().toString()));
    when(decoder.decode("valid-token")).thenReturn(jwt);
    filter.doFilter(request, response, chain);
    verify(chain).doFilter(request, response);
    assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
        .isEqualTo(jwt);
  }

  @Test
  void rejectsAnInvalidToken() throws Exception {
    assertRejected("invalid-token", "Invalid JWT");
  }

  @Test
  void rejectsAnExpiredToken() throws Exception {
    assertRejected("expired-token", "Jwt expired");
  }

  private void assertRejected(String token, String reason) throws Exception {
    var chain = mock(jakarta.servlet.FilterChain.class);
    var request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer " + token);
    var response = new MockHttpServletResponse();
    when(decoder.decode(token)).thenThrow(new BadJwtException(reason));
    filter.doFilter(request, response, chain);
    assertThat(response.getStatus()).isEqualTo(401);
    verifyNoInteractions(chain);
  }
}
