package com.soap.soap.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.model.AccessToken;
import com.soap.soap.application.port.in.LoginPort;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "security.jwt.secret=test-only-secret-with-at-least-32-bytes",
      "app.rate-limit.login.requests=2",
      "app.rate-limit.login.window=1m"
    })
@Testcontainers
@ActiveProfiles("local")
class RateLimitHttpE2ETest {
  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @LocalServerPort private int serverPort;
  @Autowired private MeterRegistry meters;
  @MockitoBean private LoginPort loginPort;

  @Test
  void loginRateLimitReturnsCommittedHttp429WithoutDispatchingToSoapOrTheUseCase()
      throws Exception {
    when(loginPort.login(any())).thenReturn(new AccessToken("test-token", "Bearer", 3600));
    var client = HttpClient.newHttpClient();

    var first = postLogin(client);
    var second = postLogin(client);
    var rejected = postLogin(client);
    var rejectedAgain = postLogin(client);

    assertThat(first.statusCode()).isEqualTo(200);
    assertThat(second.statusCode()).isEqualTo(200);
    assertThat(first.body()).contains("loginResponse");
    assertThat(second.body()).contains("loginResponse");

    assertRejected(rejected);
    assertRejected(rejectedAgain);
    verify(loginPort, times(2)).login(any());

    var counter = meters.find("security.rate_limited").tag("policy", "login").counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(2.0);
  }

  @Test
  void invalidJwtReturnsCommittedHttp401WithCorrelationIdWithoutSoapDispatch() throws Exception {
    var correlationId = "jwt-e2e-correlation";
    var body =
        """
        <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
          <soapenv:Body>
            <getReadingRequest xmlns="http://soap.com/english-reading/readings">
              <readingId>00000000-0000-0000-0000-000000000001</readingId>
            </getReadingRequest>
          </soapenv:Body>
        </soapenv:Envelope>
        """;
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + serverPort + "/ws"))
            .header("Content-Type", "text/xml; charset=UTF-8")
            .header("Authorization", "Bearer invalid-token")
            .header("X-Correlation-ID", correlationId)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();

    var response =
        HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

    assertThat(response.statusCode()).isEqualTo(401).isNotEqualTo(403);
    assertThat(response.body())
        .isEqualTo("Invalid bearer token")
        .doesNotContain("Fault", "getReadingResponse");
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValueSatisfying(
            value ->
                assertThat(value).startsWith("text/plain").containsIgnoringCase("charset=UTF-8"));
    assertThat(response.headers().firstValue("X-Correlation-ID")).contains(correlationId);
  }

  private void assertRejected(HttpResponse<String> response) {
    assertThat(response.statusCode()).isEqualTo(429).isNotEqualTo(403);
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValueSatisfying(
            value ->
                assertThat(value).startsWith("text/plain").containsIgnoringCase("charset=UTF-8"));
    assertThat(response.body())
        .isEqualTo("Too many requests")
        .doesNotContain("loginResponse", "Fault");
    assertThat(response.headers().firstValue("X-Correlation-ID"))
        .hasValueSatisfying(value -> assertThat(value).matches("[0-9a-f-]{36}"));
  }

  private HttpResponse<String> postLogin(HttpClient client) throws Exception {
    var body =
        """
        <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
          <soapenv:Body>
            <loginRequest xmlns="http://soap.com/english-reading/readings">
              <email>rate-limit@example.com</email>
              <password>secret123</password>
            </loginRequest>
          </soapenv:Body>
        </soapenv:Envelope>
        """;
    var request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + serverPort + "/ws"))
            .header("Content-Type", "text/xml; charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }
}
