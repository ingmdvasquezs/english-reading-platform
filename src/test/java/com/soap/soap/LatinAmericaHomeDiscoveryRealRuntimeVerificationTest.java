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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "security.jwt.secret=test-only-secret-with-at-least-32-bytes")
@ActiveProfiles("local")
class LatinAmericaHomeDiscoveryRealRuntimeVerificationTest {

  @Autowired private GetDiscoveryRegionOverviewPort getDiscoveryRegionOverviewPort;
  @Autowired private BrowsePlatformReadingsPort browsePlatformReadingsPort;

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
  @DisplayName(
      "1. getOverview('latin-america') returns region, CO with 15 readings, 1 topic, 2 hero images")
  void testLatinAmericaOverview() {
    var result = getDiscoveryRegionOverviewPort.getOverview("latin-america");
    var region = result.region();

    assertThat(region).isNotNull();
    assertThat(region.key()).isEqualTo("latin-america");
    assertThat(region.displayName()).isEqualTo("Latinoamérica");

    var countries = result.countries();
    assertThat(countries).hasSize(1);

    var colombia = countries.getFirst();
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

    System.out.println(
        "VERIFIED: Region "
            + region.displayName()
            + " has "
            + countries.size()
            + " active country (CO) with readingCount = "
            + colombia.readingCount());
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
  @DisplayName("3. Print real Latin America discovery readings table")
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
