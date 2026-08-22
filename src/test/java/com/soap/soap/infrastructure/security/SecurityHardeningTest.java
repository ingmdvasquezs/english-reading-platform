package com.soap.soap.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.AuthenticationRequiredException;
import com.soap.soap.application.port.out.CurrentUserPort;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ws.context.MessageContext;
import org.springframework.ws.server.endpoint.MethodEndpoint;
import org.springframework.ws.transport.context.TransportContextHolder;
import org.springframework.ws.transport.http.HttpServletConnection;

class SecurityHardeningTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-08-22T12:00:00Z"), ZoneOffset.UTC);

  @AfterEach
  void clearTransportContext() {
    TransportContextHolder.setTransportContext(null);
  }

  @Test
  void requestSizeFilterRejectsOversizedSoapBeforeTheFilterChain() throws Exception {
    var filter = new RequestSizeLimitFilter(10);
    var request = new MockHttpServletRequest("POST", "/ws");
    request.setContent("01234567890".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    var response = new MockHttpServletResponse();
    var chain = mock(jakarta.servlet.FilterChain.class);

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(413);
    verify(chain, never())
        .doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void everyUnmarkedSoapOperationIsProtectedByDefault() throws Exception {
    var currentUser = mock(CurrentUserPort.class);
    when(currentUser.requireUserId()).thenThrow(new AuthenticationRequiredException());
    var interceptor = interceptor(currentUser, 10);

    assertThatThrownBy(
            () ->
                interceptor.handleRequest(
                    mock(MessageContext.class), endpoint("protectedOperation")))
        .isInstanceOf(AuthenticationRequiredException.class);
  }

  @Test
  void publicMarkerSkipsAuthentication() throws Exception {
    var currentUser = mock(CurrentUserPort.class);

    assertThat(
            interceptor(currentUser, 10)
                .handleRequest(mock(MessageContext.class), endpoint("publicOperation")))
        .isTrue();
    verify(currentUser, never()).requireUserId();
  }

  @Test
  void loginIsLimitedByIpAndReturnsHttp429() throws Exception {
    var request = new MockHttpServletRequest();
    request.setRemoteAddr("203.0.113.10");
    var response = new MockHttpServletResponse();
    var connection = mock(HttpServletConnection.class);
    when(connection.getHttpServletRequest()).thenReturn(request);
    when(connection.getHttpServletResponse()).thenReturn(response);
    TransportContextHolder.setTransportContext(() -> connection);
    var interceptor = interceptor(mock(CurrentUserPort.class), 1);

    assertThat(interceptor.handleRequest(mock(MessageContext.class), endpoint("login"))).isTrue();
    assertThat(interceptor.handleRequest(mock(MessageContext.class), endpoint("login"))).isFalse();
    assertThat(response.getStatus()).isEqualTo(429);
  }

  @Test
  void authenticatedUsersHaveIndependentBuckets() {
    var limiter = new InMemoryRateLimiter(CLOCK, 100);

    assertThat(limiter.tryAcquire(RateLimitPolicy.LOOKUP, "user-a", 1, Duration.ofMinutes(1)))
        .isTrue();
    assertThat(limiter.tryAcquire(RateLimitPolicy.LOOKUP, "user-a", 1, Duration.ofMinutes(1)))
        .isFalse();
    assertThat(limiter.tryAcquire(RateLimitPolicy.LOOKUP, "user-b", 1, Duration.ofMinutes(1)))
        .isTrue();
  }

  private SoapSecurityInterceptor interceptor(CurrentUserPort currentUser, int loginRequests) {
    return new SoapSecurityInterceptor(
        currentUser,
        new InMemoryRateLimiter(CLOCK, 100),
        Map.of(
            RateLimitPolicy.LOGIN,
            new SoapSecurityInterceptor.Limit(loginRequests, Duration.ofMinutes(1))));
  }

  private MethodEndpoint endpoint(String methodName) throws NoSuchMethodException {
    Method method = Operations.class.getDeclaredMethod(methodName);
    return new MethodEndpoint(new Operations(), method);
  }

  private static final class Operations {
    void protectedOperation() {}

    @PublicSoapOperation
    void publicOperation() {}

    @PublicSoapOperation
    @RateLimited(RateLimitPolicy.LOGIN)
    void login() {}
  }
}
