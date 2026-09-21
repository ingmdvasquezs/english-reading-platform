package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.application.model.BrowsePlatformReadingsQuery;
import com.soap.soap.application.model.GetDiscoveryHomeQuery;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.port.in.BrowsePlatformReadingsPort;
import com.soap.soap.application.port.in.GetDiscoveryHomePort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.domain.model.DiscoveryTopic;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.EditorialStatus;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.RightsStatus;
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
class MexicoEditorialCatalogIntegrationTest {

  @Autowired private ReadingRepositoryPort readingRepository;
  @Autowired private BrowsePlatformReadingsPort browsePort;
  @Autowired private GetDiscoveryHomePort getDiscoveryHomePort;

  private final UUID testUserId = UUID.fromString("9413b655-f4ec-40c6-85f7-85508999cd2f");

  @BeforeEach
  void authenticate() {
    var jwt =
        Jwt.withTokenValue("mock-token")
            .header("alg", "none")
            .subject(testUserId.toString())
            .claim("sub", testUserId.toString())
            .build();
    var authentication = new UsernamePasswordAuthenticationToken(jwt, null, List.of());
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  @AfterEach
  void clearAuth() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("1. Exactly 5 published platform readings exist for Mexico with valid metadata")
  void testMexicoReadingsMetadata() {
    List<Reading> allPlatform = readingRepository.findAllPlatformReadings();

    var mxReadings = allPlatform.stream().filter(r -> "MX".equals(r.countryCode())).toList();
    assertThat(mxReadings).hasSize(5);

    for (Reading r : mxReadings) {
      assertThat(r.origin()).isEqualTo(ReadingOrigin.PLATFORM);
      assertThat(r.editorialStatus()).isEqualTo(EditorialStatus.PUBLISHED);
      assertThat(r.rightsStatus()).isEqualTo(RightsStatus.ORIGINAL);
      assertThat(r.editorialLevel()).isEqualTo(EditorialLevel.B1);
      assertThat(r.category()).isNotBlank();
      assertThat(r.discoveryTopic()).isNotNull();
      assertThat(r.coverKey()).isNotBlank();
      assertThat(r.content()).isNotBlank();
      assertThat(r.shortDescription()).isNotBlank();
    }

    var titles = mxReadings.stream().map(Reading::title).toList();
    assertThat(titles)
        .containsExactlyInAnyOrder(
            "The Mountain That Smokes",
            "The City Beside the Sacred Wells",
            "When the Dead Come Home",
            "A Journey Across a Continent",
            "The Animal That Refuses to Grow Up");
  }

  @Test
  @DisplayName("2. Browse flow retrieves Mexican readings by country and topics")
  void testBrowseMexicoFlow() {
    var page =
        browsePort.browsePlatformReadings(
            new BrowsePlatformReadingsQuery(null, null, null, "MX", null, new PageRequest(0, 10)));

    assertThat(page.totalElements()).isEqualTo(5);
    assertThat(page.content()).hasSize(5);
    assertThat(page.content()).allMatch(r -> "MX".equals(r.countryCode()));

    // Browse with specific topic
    var mythsPage =
        browsePort.browsePlatformReadings(
            new BrowsePlatformReadingsQuery(
                null, null, null, "MX", DiscoveryTopic.MYTHS_AND_LEGENDS, new PageRequest(0, 10)));
    assertThat(mythsPage.totalElements()).isEqualTo(1);
    assertThat(mythsPage.content().getFirst().title()).isEqualTo("The Mountain That Smokes");
  }

  @Test
  @DisplayName("3. Colombian readings remain intact (15 readings, all CO, unchanged)")
  void testColombiaReadingsIntact() {
    List<Reading> allPlatform = readingRepository.findAllPlatformReadings();

    var coReadings = allPlatform.stream().filter(r -> "CO".equals(r.countryCode())).toList();
    assertThat(coReadings).hasSize(15);
    assertThat(coReadings).allMatch(r -> r.editorialStatus() == EditorialStatus.PUBLISHED);
    assertThat(coReadings).allMatch(r -> r.discoveryTopic() == DiscoveryTopic.MYTHS_AND_LEGENDS);
  }

  @Test
  @DisplayName(
      "4. GetDiscoveryHome aggregation excludes Mexican and Colombian readings from general shelves")
  void testDiscoveryHomeWithBothCountries() {
    var result = getDiscoveryHomePort.getDiscoveryHome(new GetDiscoveryHomeQuery(10, 8, 8));

    assertThat(result).isNotNull();
    assertThat(result.latinAmerica().countries()).hasSize(2);
    assertThat(result.latinAmerica().defaultCountryCode()).isEqualTo("CO");

    // All Mexican and Colombian readings belong to the specialized regional block
    // and must NOT appear in any general Home shelves (including dynamic "new" shelf)
    var newShelf = result.shelves().stream().filter(s -> "new".equals(s.key())).findFirst();
    assertThat(newShelf).isEmpty();

    // Verify absolutely no shelf in result.shelves() contains Mexican readings
    for (var shelf : result.shelves()) {
      var mxInShelf =
          shelf.readings().stream().filter(r -> "MX".equalsIgnoreCase(r.countryCode())).toList();
      assertThat(mxInShelf).isEmpty();
    }
  }
}
