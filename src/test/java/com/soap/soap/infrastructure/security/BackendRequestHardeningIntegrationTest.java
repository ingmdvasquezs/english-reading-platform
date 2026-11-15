package com.soap.soap.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Real HTTP regression tests (real XSD validation, real BCrypt, real PostgreSQL) for request and
 * password hardening: BCrypt 72-byte limit on SOAP register/login and REST malformed requests.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "security.jwt.secret=test-only-secret-with-at-least-32-bytes",
      "app.rate-limit.login.requests=100",
      "app.rate-limit.register.requests=100"
    })
@Testcontainers
@ActiveProfiles("local")
class BackendRequestHardeningIntegrationTest {
  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  private static final java.nio.charset.Charset UTF8 = StandardCharsets.UTF_8;
  private static final Pattern FAULT_CODE =
      Pattern.compile("<faultcode>(?:[\\w-]+:)?(\\w+)</faultcode>");
  private static final Pattern FAULT_STRING =
      Pattern.compile("<faultstring[^>]*>([^<]*)</faultstring>");
  private static final Pattern ACCESS_TOKEN =
      Pattern.compile("<(?:\\w+:)?accessToken>([^<]+)</(?:\\w+:)?accessToken>");

  private final HttpClient client = HttpClient.newHttpClient();

  @LocalServerPort private int serverPort;
  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("BACK-009: a 72-byte ASCII password registers and logs in with real BCrypt")
  void asciiPasswordAtTheBcryptLimitRegistersAndLogsIn() throws Exception {
    var email = uniqueEmail();
    var password = "a".repeat(72);

    assertThat(register(email, password).statusCode()).isEqualTo(200);
    var login = login(email, password);

    assertThat(login.statusCode()).isEqualTo(200);
    assertThat(ACCESS_TOKEN.matcher(login.body()).find()).isTrue();
  }

  @Test
  @DisplayName("BACK-009: a 72-byte multibyte UTF-8 password registers and logs in")
  void multibytePasswordAtTheBcryptLimitRegistersAndLogsIn() throws Exception {
    var email = uniqueEmail();
    var password = "é".repeat(36); // 36 characters, 72 UTF-8 bytes

    assertThat(password.getBytes(StandardCharsets.UTF_8)).hasSize(72);
    assertThat(register(email, password).statusCode()).isEqualTo(200);
    assertThat(login(email, password).statusCode()).isEqualTo(200);
  }

  @Test
  @DisplayName("BACK-009: register above 72 bytes is a client fault, never a server fault")
  void registerAboveTheBcryptLimitIsAClientFault() throws Exception {
    var ascii = "a".repeat(73);
    var multibyte = "€".repeat(25); // 25 characters, 75 UTF-8 bytes
    assertThat(multibyte.getBytes(StandardCharsets.UTF_8)).hasSize(75);

    for (var password : new String[] {ascii, multibyte}) {
      var email = uniqueEmail();
      var response = register(email, password);

      assertClientFault(response);
      assertThat(faultString(response)).contains("72 bytes");
      assertThat(response.body()).doesNotContain("Internal server error", "BCrypt", "Exception");
      assertThat(
              jdbc.queryForObject("select count(*) from users where email = ?", Long.class, email))
          .isZero();
    }
  }

  @Test
  @DisplayName("BACK-009: login above 72 bytes is invalid credentials for known and unknown emails")
  void loginAboveTheBcryptLimitDoesNotReachBcryptOrRevealAccounts() throws Exception {
    var knownEmail = uniqueEmail();
    assertThat(register(knownEmail, "secret123").statusCode()).isEqualTo(200);

    for (var password : new String[] {"a".repeat(73), "€".repeat(25)}) {
      var known = login(knownEmail, password);
      var unknown = login(uniqueEmail(), password);

      assertClientFault(known);
      assertClientFault(unknown);
      assertThat(faultString(known)).isEqualTo("Invalid credentials");
      assertThat(faultString(unknown)).isEqualTo(faultString(known));
      assertThat(known.statusCode()).isEqualTo(unknown.statusCode());
    }
  }

  @Test
  @DisplayName("Malformed JSON on PUT progress returns 400 INVALID_REQUEST without parser details")
  void malformedJsonReturnsBadRequest() throws Exception {
    var token = createToken();
    var request =
        HttpRequest.newBuilder(
                URI.create(baseUrl() + "/api/v1/documents/" + UUID.randomUUID() + "/progress"))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .header("X-Correlation-ID", "malformed-json-e2e")
            .PUT(HttpRequest.BodyPublishers.ofString("{\"currentUnitId\":\"abc\n\",", UTF8))
            .build();

    var response = client.send(request, HttpResponse.BodyHandlers.ofString(UTF8));

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.headers().firstValue("Content-Type").orElse(""))
        .contains("application/json");
    assertThat(response.body())
        .contains("\"code\":\"INVALID_REQUEST\"")
        .contains("\"requestId\":\"malformed-json-e2e\"")
        .doesNotContainIgnoringCase("jackson")
        .doesNotContainIgnoringCase("json parse")
        .doesNotContain("Exception", "com.fasterxml", "tools.jackson", "line:");
  }

  @Test
  @DisplayName("Malformed UUID path returns 400 and an unknown API route is never a 500")
  void frameworkClientErrorsAreNotInternalErrors() throws Exception {
    var token = createToken();

    var malformedId = get("/api/v1/documents/not-a-uuid/progress", token);
    assertThat(malformedId.statusCode()).isEqualTo(400);
    assertThat(malformedId.body())
        .contains("\"code\":\"INVALID_REQUEST\"")
        .doesNotContain("Exception", "not-a-uuid");

    // Unmapped routes never reach the REST advice: Spring's /error dispatch is denied by the
    // security chain (anyRequest().denyAll()), which is the pre-existing, intentional behaviour.
    var unknownRoute = get("/api/v1/documents/" + UUID.randomUUID() + "/does-not-exist", token);
    assertThat(unknownRoute.statusCode()).isIn(403, 404);
    assertThat(unknownRoute.body()).doesNotContain("Exception", "INTERNAL_ERROR");
  }

  private String createToken() throws Exception {
    var email = uniqueEmail();
    assertThat(register(email, "secret123").statusCode()).isEqualTo(200);
    var matcher = ACCESS_TOKEN.matcher(login(email, "secret123").body());
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }

  private HttpResponse<String> register(String email, String password) throws Exception {
    return postSoap(
        "<registerUserRequest xmlns=\"http://soap.com/english-reading/readings\">"
            + "<name>Ada</name><email>"
            + email
            + "</email><password>"
            + password
            + "</password></registerUserRequest>");
  }

  private HttpResponse<String> login(String email, String password) throws Exception {
    return postSoap(
        "<loginRequest xmlns=\"http://soap.com/english-reading/readings\"><email>"
            + email
            + "</email><password>"
            + password
            + "</password></loginRequest>");
  }

  private HttpResponse<String> postSoap(String payload) throws Exception {
    var envelope =
        "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            + "<soapenv:Body>"
            + payload
            + "</soapenv:Body></soapenv:Envelope>";
    var request =
        HttpRequest.newBuilder(URI.create(baseUrl() + "/ws"))
            .header("Content-Type", "text/xml; charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofString(envelope, UTF8))
            .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString(UTF8));
  }

  private HttpResponse<String> get(String path, String token) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create(baseUrl() + path))
            .header("Authorization", "Bearer " + token)
            .GET()
            .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString(UTF8));
  }

  private static void assertClientFault(HttpResponse<String> response) {
    var code = FAULT_CODE.matcher(response.body());
    assertThat(code.find()).as("SOAP fault expected in: %s", response.body()).isTrue();
    assertThat(code.group(1)).isEqualTo("Client");
  }

  private static String faultString(HttpResponse<String> response) {
    var matcher = FAULT_STRING.matcher(response.body());
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }

  private static String uniqueEmail() {
    return "hardening-" + UUID.randomUUID() + "@example.com";
  }

  private String baseUrl() {
    return "http://localhost:" + serverPort;
  }
}
