package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.application.model.BrowsePlatformReadingsQuery;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.port.in.BrowsePlatformReadingsPort;
import com.soap.soap.application.port.in.ListCollectionReadingsPort;
import com.soap.soap.application.port.in.ListCollectionsPort;
import com.soap.soap.domain.model.EditorialCategory;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.PlatformReadingSort;
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
class ExploreV1RealRuntimeVerificationTest {

  @Autowired private ListCollectionsPort listCollectionsPort;
  @Autowired private BrowsePlatformReadingsPort browsePlatformReadingsPort;
  @Autowired private ListCollectionReadingsPort listCollectionReadingsPort;

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
  @DisplayName("1. listCollections returns colombian-myths-legends with readingCount = 15")
  void testListCollectionsReadingCount() {
    var collections = listCollectionsPort.listCollections();
    assertThat(collections).isNotEmpty();

    var colombia =
        collections.stream().filter(c -> "colombian-myths-legends".equals(c.key())).findFirst();

    assertThat(colombia).isPresent();
    assertThat(colombia.get().readingCount()).isEqualTo(15);
    System.out.println(
        "VERIFIED: colombian-myths-legends readingCount = " + colombia.get().readingCount());
  }

  @Test
  @DisplayName("2. browsePlatformReadings with no filters returns 15 readings")
  void testBrowseNoFilters() {
    var page =
        browsePlatformReadingsPort.browsePlatformReadings(
            new BrowsePlatformReadingsQuery(null, null, null, new PageRequest(0, 20)));

    assertThat(page.totalElements()).isEqualTo(15);
    assertThat(page.content()).hasSize(15);
    System.out.println("VERIFIED: browse no filters totalElements = " + page.totalElements());
  }

  @Test
  @DisplayName("3. browsePlatformReadings with B1 returns 6 readings")
  void testBrowseLevelB1() {
    var page =
        browsePlatformReadingsPort.browsePlatformReadings(
            new BrowsePlatformReadingsQuery(null, null, EditorialLevel.B1, new PageRequest(0, 20)));

    assertThat(page.totalElements()).isEqualTo(6);
    assertThat(page.content()).hasSize(6);
    assertThat(page.content()).allMatch(r -> r.editorialLevel() == EditorialLevel.B1);
    System.out.println("VERIFIED: browse level B1 totalElements = " + page.totalElements());
  }

  @Test
  @DisplayName("4. browsePlatformReadings with B2 returns 9 readings")
  void testBrowseLevelB2() {
    var page =
        browsePlatformReadingsPort.browsePlatformReadings(
            new BrowsePlatformReadingsQuery(null, null, EditorialLevel.B2, new PageRequest(0, 20)));

    assertThat(page.totalElements()).isEqualTo(9);
    assertThat(page.content()).hasSize(9);
    assertThat(page.content()).allMatch(r -> r.editorialLevel() == EditorialLevel.B2);
    System.out.println("VERIFIED: browse level B2 totalElements = " + page.totalElements());
  }

  @Test
  @DisplayName(
      "5. browsePlatformReadings with category CULTURE_ARTS_AND_FICTION returns 15 readings")
  void testBrowseCategory() {
    var page =
        browsePlatformReadingsPort.browsePlatformReadings(
            new BrowsePlatformReadingsQuery(
                null, EditorialCategory.CULTURE_ARTS_AND_FICTION, null, new PageRequest(0, 20)));

    assertThat(page.totalElements()).isEqualTo(15);
    assertThat(page.content()).hasSize(15);
    assertThat(page.content())
        .allMatch(r -> "Culture, Arts & Fiction".equalsIgnoreCase(r.category()));
    System.out.println(
        "VERIFIED: browse category Culture, Arts & Fiction totalElements = "
            + page.totalElements());
  }

  @Test
  @DisplayName("6. browsePlatformReadings with category + B1 returns 6 readings")
  void testBrowseCategoryAndB1() {
    var page =
        browsePlatformReadingsPort.browsePlatformReadings(
            new BrowsePlatformReadingsQuery(
                null,
                EditorialCategory.CULTURE_ARTS_AND_FICTION,
                EditorialLevel.B1,
                new PageRequest(0, 20)));

    assertThat(page.totalElements()).isEqualTo(6);
    assertThat(page.content()).hasSize(6);
    assertThat(page.content())
        .allMatch(
            r ->
                "Culture, Arts & Fiction".equalsIgnoreCase(r.category())
                    && r.editorialLevel() == EditorialLevel.B1);
    System.out.println("VERIFIED: browse category + B1 totalElements = " + page.totalElements());
  }

  @Test
  @DisplayName(
      "7. browsePlatformReadings with collection colombian-myths-legends returns 15 readings")
  void testBrowseCollection() {
    var page =
        browsePlatformReadingsPort.browsePlatformReadings(
            new BrowsePlatformReadingsQuery(
                "colombian-myths-legends", null, null, new PageRequest(0, 20)));

    assertThat(page.totalElements()).isEqualTo(15);
    assertThat(page.content()).hasSize(15);
    System.out.println(
        "VERIFIED: browse collection colombian-myths-legends totalElements = "
            + page.totalElements());
  }

  @Test
  @DisplayName("8. browsePlatformReadings with collection + B2 returns 9 readings")
  void testBrowseCollectionAndB2() {
    var page =
        browsePlatformReadingsPort.browsePlatformReadings(
            new BrowsePlatformReadingsQuery(
                "colombian-myths-legends", null, EditorialLevel.B2, new PageRequest(0, 20)));

    assertThat(page.totalElements()).isEqualTo(9);
    assertThat(page.content()).hasSize(9);
    assertThat(page.content()).allMatch(r -> r.editorialLevel() == EditorialLevel.B2);
    System.out.println("VERIFIED: browse collection + B2 totalElements = " + page.totalElements());
  }

  @Test
  @DisplayName("9. V2 fit parity confirmed with listCollectionReadings")
  void testV2ParityWithListCollectionReadings() {
    var collectionPage =
        listCollectionReadingsPort.listCollectionReadings(
            "colombian-myths-legends", new PageRequest(0, 15));
    var browsePage =
        browsePlatformReadingsPort.browsePlatformReadings(
            new BrowsePlatformReadingsQuery(
                "colombian-myths-legends", null, null, new PageRequest(0, 15)));

    assertThat(collectionPage.content()).isNotEmpty();
    assertThat(browsePage.content()).hasSize(collectionPage.content().size());

    for (int i = 0; i < collectionPage.content().size(); i++) {
      var colCard = collectionPage.content().get(i);
      var browseCard = browsePage.content().get(i);

      assertThat(browseCard.readingId()).isEqualTo(colCard.readingId());
      assertThat(browseCard.title()).isEqualTo(colCard.title());
      assertThat(browseCard.vocabularyFitPercentage()).isEqualTo(colCard.vocabularyFitPercentage());
      assertThat(browseCard.classificationConfidencePercentage())
          .isEqualTo(colCard.classificationConfidencePercentage());
      assertThat(browseCard.reasonCode()).isEqualTo(colCard.reasonCode());
      assertThat(browseCard.knownWords()).isEqualTo(colCard.knownWords());
      assertThat(browseCard.learningWords()).isEqualTo(colCard.learningWords());
      assertThat(browseCard.progressStatus()).isEqualTo(colCard.progressStatus());
      assertThat(browseCard.coverKey()).isEqualTo(colCard.coverKey());
    }
    System.out.println(
        "VERIFIED: Exact V2 parity confirmed for all 15 readings between listCollectionReadings and browsePlatformReadings!");
  }

  @Test
  @DisplayName("10. Print real runtime card sample")
  void testPrintRealReadingSample() {
    var page =
        browsePlatformReadingsPort.browsePlatformReadings(
            new BrowsePlatformReadingsQuery(null, null, null, new PageRequest(0, 1)));

    assertThat(page.content()).isNotEmpty();
    var sample = page.content().getFirst();

    System.out.println("=== REAL RUNTIME READING SAMPLE ===");
    System.out.println("readingId: " + sample.readingId());
    System.out.println("title: " + sample.title());
    System.out.println("editorialLevel: " + sample.editorialLevel());
    System.out.println("category: " + sample.category());
    System.out.println("coverKey: " + sample.coverKey());
    System.out.println("vocabularyFitPercentage: " + sample.vocabularyFitPercentage() + "%");
    System.out.println(
        "classificationConfidencePercentage: " + sample.classificationConfidencePercentage() + "%");
    System.out.println("reasonCode: " + sample.reasonCode());
    System.out.println("progressStatus: " + sample.progressStatus());
    System.out.println("===================================");
  }

  @Test
  @DisplayName(
      "11. browsePlatformReadings with sort = CREATED_AT_DESC applies DB ordering before pagination across pages")
  void testBrowsePlatformReadingsSortCreatedAtDescAcrossPages() {
    // Page 0 (size 5)
    var page0 =
        browsePlatformReadingsPort.browsePlatformReadings(
            new BrowsePlatformReadingsQuery(
                null,
                null,
                null,
                null,
                null,
                PlatformReadingSort.CREATED_AT_DESC,
                new PageRequest(0, 5)));
    assertThat(page0.content()).hasSize(5);

    // Page 1 (size 5)
    var page1 =
        browsePlatformReadingsPort.browsePlatformReadings(
            new BrowsePlatformReadingsQuery(
                null,
                null,
                null,
                null,
                null,
                PlatformReadingSort.CREATED_AT_DESC,
                new PageRequest(1, 5)));
    assertThat(page1.content()).hasSize(5);

    // Within page 0: non-ascending createdAt
    for (int i = 0; i < page0.content().size() - 1; i++) {
      var current = page0.content().get(i).createdAt();
      var next = page0.content().get(i + 1).createdAt();
      if (current != null && next != null) {
        assertThat(current).isAfterOrEqualTo(next);
      }
    }

    // Boundary between page 0 last item and page 1 first item
    var lastOfPage0 = page0.content().get(4).createdAt();
    var firstOfPage1 = page1.content().get(0).createdAt();
    if (lastOfPage0 != null && firstOfPage1 != null) {
      assertThat(lastOfPage0).isAfterOrEqualTo(firstOfPage1);
    }

    // No duplicate reading IDs between page 0 and page 1
    var page0Ids = page0.content().stream().map(c -> c.readingId()).toList();
    var page1Ids = page1.content().stream().map(c -> c.readingId()).toList();
    assertThat(page0Ids).doesNotContainAnyElementsOf(page1Ids);
  }

  @Test
  @DisplayName(
      "12. Collection deep browse with sort = CREATED_AT_DESC sorts by createdAt instead of collection displayOrder")
  void testCollectionSortCreatedAtDescOverridesDisplayOrder() {
    var defaultSorted =
        browsePlatformReadingsPort.browsePlatformReadings(
            new BrowsePlatformReadingsQuery(
                "colombian-myths-legends",
                null,
                null,
                null,
                null,
                PlatformReadingSort.DEFAULT,
                new PageRequest(0, 15)));

    var newestSorted =
        browsePlatformReadingsPort.browsePlatformReadings(
            new BrowsePlatformReadingsQuery(
                "colombian-myths-legends",
                null,
                null,
                null,
                null,
                PlatformReadingSort.CREATED_AT_DESC,
                new PageRequest(0, 15)));

    assertThat(defaultSorted.content()).hasSize(15);
    assertThat(newestSorted.content()).hasSize(15);

    // Both contain the same readings
    assertThat(newestSorted.content().stream().map(c -> c.readingId()).toList())
        .containsExactlyInAnyOrderElementsOf(
            defaultSorted.content().stream().map(c -> c.readingId()).toList());

    // Newest sorted is ordered by createdAt descending
    for (int i = 0; i < newestSorted.content().size() - 1; i++) {
      var current = newestSorted.content().get(i).createdAt();
      var next = newestSorted.content().get(i + 1).createdAt();
      if (current != null && next != null) {
        assertThat(current).isAfterOrEqualTo(next);
      }
    }
  }
}
