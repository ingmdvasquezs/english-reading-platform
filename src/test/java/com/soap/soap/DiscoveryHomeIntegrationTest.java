package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.application.model.DiscoveryHomeResult;
import com.soap.soap.application.model.GetDiscoveryHomeQuery;
import com.soap.soap.application.port.in.GetDiscoveryHomePort;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "security.jwt.secret=test-only-secret-with-at-least-32-bytes")
@ActiveProfiles("local")
class DiscoveryHomeIntegrationTest {

  @Autowired private GetDiscoveryHomePort getDiscoveryHomePort;

  // Use andres@gmail.com from dev DB
  private final UUID andresUserId = UUID.fromString("9413b655-f4ec-40c6-85f7-85508999cd2f");

  @BeforeEach
  void authenticate() {
    var jwt =
        Jwt.withTokenValue("mock-token")
            .header("alg", "none")
            .subject(andresUserId.toString())
            .claim("sub", andresUserId.toString())
            .build();
    var authentication = new UsernamePasswordAuthenticationToken(jwt, null, List.of());
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  @AfterEach
  void clearAuth() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("getDiscoveryHome returns aggregated Home discovery for real runtime DB")
  void testGetDiscoveryHomeRealDb() {
    var query = new GetDiscoveryHomeQuery(10, 8, 8);
    DiscoveryHomeResult result = getDiscoveryHomePort.getDiscoveryHome(query);

    assertThat(result).isNotNull();

    // 1. For You
    assertThat(result.forYou()).isNotNull();

    // 2. Latin America Specialized Block
    assertThat(result.latinAmerica()).isNotNull();
    assertThat(result.latinAmerica().region().key()).isEqualTo("latin-america");
    assertThat(result.latinAmerica().region().displayName()).isEqualTo("Latinoamérica");
    assertThat(result.latinAmerica().defaultCountryCode()).isEqualTo("CO");
    assertThat(result.latinAmerica().defaultTopicKey()).isEqualTo("MYTHS_AND_LEGENDS");

    // Real DB countries: CO (15 readings, 1 topic, 2 hero images) and MX (5 readings, 5 topics, 2
    // hero images)
    assertThat(result.latinAmerica().countries()).hasSize(2);
    var co = result.latinAmerica().countries().get(0);
    assertThat(co.countryCode()).isEqualTo("CO");
    assertThat(co.readingCount()).isEqualTo(15);
    assertThat(co.topics()).hasSize(1);
    assertThat(co.topics().get(0).key()).isEqualTo("MYTHS_AND_LEGENDS");
    assertThat(co.heroImages()).hasSize(2);

    var mx = result.latinAmerica().countries().get(1);
    assertThat(mx.countryCode()).isEqualTo("MX");
    assertThat(mx.readingCount()).isEqualTo(5);
    assertThat(mx.topics()).hasSize(5);
    assertThat(mx.heroImages()).hasSize(2);

    // Default country preview: exactly 8 readings from Colombia
    assertThat(result.latinAmerica().readings()).hasSize(8);
    assertThat(result.latinAmerica().readings()).allMatch(r -> "CO".equals(r.countryCode()));

    // 3. Dynamic "Nuevas lecturas":
    // All 20 published platform readings currently belong to regional Latin America countries (15
    // CO, 5 MX).
    // Per product business rules, content associated with any country in the specialized regional
    // block
    // must NOT appear in general Home shelves. Therefore, the dynamic "new" shelf is omitted.
    var newShelf = result.shelves().stream().filter(s -> "new".equals(s.key())).findFirst();
    assertThat(newShelf).isEmpty();

    // 4. Generic editorial shelves:
    // colombian-myths-legends is suppressed from generic Home shelves because it is owned by Latin
    // America.
    var colShelf =
        result.shelves().stream()
            .filter(s -> "colombian-myths-legends".equals(s.key()))
            .findFirst();
    assertThat(colShelf).isEmpty();
  }
}
