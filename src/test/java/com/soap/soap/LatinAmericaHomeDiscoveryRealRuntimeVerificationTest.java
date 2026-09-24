package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.application.model.BrowsePlatformReadingsQuery;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.port.in.BrowsePlatformReadingsPort;
import com.soap.soap.application.port.in.GetDiscoveryRegionOverviewPort;
import com.soap.soap.domain.model.DiscoveryTopic;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "security.jwt.secret=test-only-secret-with-at-least-32-bytes")
@Testcontainers
@ActiveProfiles("local")
class LatinAmericaHomeDiscoveryRealRuntimeVerificationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private GetDiscoveryRegionOverviewPort getDiscoveryRegionOverviewPort;
  @Autowired private BrowsePlatformReadingsPort browsePlatformReadingsPort;
  @Autowired private EditorialTestFixtureHelper fixtureHelper;

  private final UUID testUserId = EditorialTestFixtureHelper.TEST_USER_ID;

  @BeforeEach
  void setUp() {
    fixtureHelper.seedCatalogIfNeeded();
    authenticate();
  }

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
  @DisplayName(
      "1. getOverview('latin-america') returns region with CO (15 readings) and MX (5 readings)")
  void testLatinAmericaOverview() {
    var result = getDiscoveryRegionOverviewPort.getOverview("latin-america");
    var region = result.region();

    assertThat(region).isNotNull();
    assertThat(region.key()).isEqualTo("latin-america");
    assertThat(region.displayName()).isEqualTo("Latinoamérica");

    var countries = result.countries();
    assertThat(countries).hasSize(2);

    // 1. Colombia (displayOrder = 1)
    var colombia = countries.get(0);
    assertThat(colombia.countryCode()).isEqualTo("CO");
    assertThat(colombia.displayName()).isEqualTo("Colombia");
    assertThat(colombia.readingCount()).isEqualTo(15);
    assertThat(colombia.heroImages()).hasSize(2);
    assertThat(colombia.heroImages().getFirst().location()).isEqualTo("Villa de Leyva, Boyacá");

    assertThat(colombia.topics()).hasSize(1);
    var topic = colombia.topics().getFirst();
    assertThat(topic.key()).isEqualTo(DiscoveryTopic.MYTHS_AND_LEGENDS.name());
    assertThat(topic.displayName()).isEqualTo("Mitos y leyendas");
    assertThat(topic.readingCount()).isEqualTo(15);

    // 2. Mexico (displayOrder = 2)
    var mexico = countries.get(1);
    assertThat(mexico.countryCode()).isEqualTo("MX");
    assertThat(mexico.displayName()).isEqualTo("México");
    assertThat(mexico.readingCount()).isEqualTo(5);
    assertThat(mexico.heroImages()).hasSize(2);
    assertThat(mexico.topics()).hasSize(5);

    System.out.println(
        "VERIFIED: Region "
            + region.displayName()
            + " has "
            + countries.size()
            + " active countries: CO ("
            + colombia.readingCount()
            + "), MX ("
            + mexico.readingCount()
            + ")");
  }

  @Test
  @DisplayName(
      "2. browsePlatformReadings with countryCode CO and topic MYTHS_AND_LEGENDS returns 15 readings")
  void testBrowseCountryAndTopic() {
    var page =
        browsePlatformReadingsPort.browsePlatformReadings(
            new BrowsePlatformReadingsQuery(
                null, null, null, "CO", DiscoveryTopic.MYTHS_AND_LEGENDS, new PageRequest(0, 20)));

    assertThat(page.totalElements()).isEqualTo(15);
    assertThat(page.content()).hasSize(15);
    assertThat(page.content()).allMatch(r -> "CO".equals(r.countryCode()));
    assertThat(page.content())
        .allMatch(r -> r.discoveryTopic() == DiscoveryTopic.MYTHS_AND_LEGENDS);
    assertThat(page.content()).allMatch(r -> r.vocabularyFitPercentage() != null);

    System.out.println(
        "VERIFIED: browse CO + MYTHS_AND_LEGENDS totalElements = " + page.totalElements());
  }

  @Test
  @DisplayName("3. browsePlatformReadings with countryCode MX returns 5 published readings")
  void testBrowseMexicoReadings() {
    var page =
        browsePlatformReadingsPort.browsePlatformReadings(
            new BrowsePlatformReadingsQuery(null, null, null, "MX", null, new PageRequest(0, 20)));

    assertThat(page.totalElements()).isEqualTo(5);
    assertThat(page.content()).hasSize(5);
    assertThat(page.content()).allMatch(r -> "MX".equals(r.countryCode()));
    assertThat(page.content()).allMatch(r -> r.vocabularyFitPercentage() != null);

    // Verify 5 distinct topics
    var topics = page.content().stream().map(r -> r.discoveryTopic()).toList();
    assertThat(topics)
        .containsExactlyInAnyOrder(
            DiscoveryTopic.MYTHS_AND_LEGENDS,
            DiscoveryTopic.HISTORY_AND_MEMORY,
            DiscoveryTopic.CULTURE_AND_TRADITIONS,
            DiscoveryTopic.REAL_STORIES,
            DiscoveryTopic.NATURE_AND_PLACES);

    System.out.println("VERIFIED: browse MX totalElements = " + page.totalElements());
  }

  @Test
  @DisplayName("4. Print real Latin America discovery readings table")
  void testPrintReadingsTable() {
    var page =
        browsePlatformReadingsPort.browsePlatformReadings(
            new BrowsePlatformReadingsQuery(
                null, null, null, "CO", DiscoveryTopic.MYTHS_AND_LEGENDS, new PageRequest(0, 20)));

    System.out.println(
        "-------------------------------------------------------------------------------------------------------------------------");
    System.out.printf(
        "%-36s | %-20s | %-4s | %-18s | %-5s | %-10s | %-12s%n",
        "readingId", "title", "code", "discoveryTopic", "level", "vocabFit", "progress");
    System.out.println(
        "-------------------------------------------------------------------------------------------------------------------------");
    for (var r : page.content()) {
      System.out.printf(
          "%-36s | %-20s | %-4s | %-18s | %-5s | %-10s | %-12s%n",
          r.readingId(),
          r.title().length() > 20 ? r.title().substring(0, 17) + "..." : r.title(),
          r.countryCode(),
          r.discoveryTopic(),
          r.editorialLevel(),
          r.vocabularyFitPercentage() != null ? r.vocabularyFitPercentage() + "%" : "N/A",
          r.progressStatus() != null ? r.progressStatus() : "NOT_STARTED");
    }
    System.out.println(
        "-------------------------------------------------------------------------------------------------------------------------");
  }
}
