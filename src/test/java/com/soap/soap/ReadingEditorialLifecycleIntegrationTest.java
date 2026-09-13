package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soap.soap.application.exception.ReadingNotFoundException;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.port.in.GetReadingComprehensionQuizPort;
import com.soap.soap.application.port.in.GetReadingPort;
import com.soap.soap.application.port.in.GetReadingReaderDataPort;
import com.soap.soap.application.port.in.ListCollectionReadingsPort;
import com.soap.soap.application.port.in.ListCollectionsPort;
import com.soap.soap.application.port.in.ListContinueReadingPort;
import com.soap.soap.application.port.in.ListPlatformReadingsPort;
import com.soap.soap.application.port.in.RecommendPlatformReadingsPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.service.ReadingLexicalIndexer;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.EditorialStatus;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.ReadingProgressStatus;
import com.soap.soap.domain.model.User;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "security.jwt.secret=test-only-secret-with-at-least-32-bytes")
@Testcontainers
@ActiveProfiles("local")
@Transactional
class ReadingEditorialLifecycleIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private ListPlatformReadingsPort listPlatformReadings;
  @Autowired private RecommendPlatformReadingsPort recommendReadings;
  @Autowired private GetReadingPort getReading;
  @Autowired private GetReadingReaderDataPort getReaderData;
  @Autowired private ListContinueReadingPort continueReading;
  @Autowired private ListCollectionsPort listCollections;
  @Autowired private ListCollectionReadingsPort listCollectionReadings;
  @Autowired private GetReadingComprehensionQuizPort getQuiz;
  @Autowired private ReadingRepositoryPort readings;
  @Autowired private ReadingProgressRepositoryPort progress;
  @Autowired private UserRepositoryPort users;
  @Autowired private ReadingLexicalIndexer indexer;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entityManager;

  private User testUser;
  private User otherUser;

  @BeforeEach
  void setUp() {
    testUser =
        users.save(
            new User(
                null,
                "lifecycle_user_" + UUID.randomUUID(),
                "lifecycle_" + UUID.randomUUID() + "@example.com",
                "hash",
                null));
    otherUser =
        users.save(
            new User(
                null,
                "other_user_" + UUID.randomUUID(),
                "other_" + UUID.randomUUID() + "@example.com",
                "hash",
                null));
    entityManager.flush();
    authenticate(testUser.id());
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private void authenticate(UUID userId) {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(userId.toString())
            .claim("role", "ROLE_USER")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(jwt, "token", List.of()));
  }

  @Test
  @DisplayName("A. PLATFORM PUBLISHED: visible in catalog, recommendations, and reader")
  void testPlatformPublishedLifecycle() {
    var reading =
        readings.save(
            new Reading(
                null,
                null,
                "Published Article " + UUID.randomUUID(),
                "The sun shines brightly over the mountain peak.",
                "en",
                LocalDateTime.now(),
                ReadingOrigin.PLATFORM,
                EditorialLevel.A1,
                "NATURE",
                "pub-cover",
                EditorialStatus.PUBLISHED));
    entityManager.flush();
    indexer.indexReading(reading.id(), reading.language(), reading.content());

    // Visible in listPlatformReadings
    var catalogPage = listPlatformReadings.listPlatformReadings(new PageRequest(0, 100));
    assertThat(catalogPage.content().stream().anyMatch(r -> r.id().equals(reading.id()))).isTrue();

    // Visible in recommendPlatformReadings
    var recPage = recommendReadings.recommendPlatformReadings(new PageRequest(0, 100));
    assertThat(recPage.content().stream().anyMatch(r -> r.readingId().equals(reading.id())))
        .isTrue();

    // Accessible in getReading
    var loaded = getReading.getReading(reading.id());
    assertThat(loaded.id()).isEqualTo(reading.id());
    assertThat(loaded.editorialStatus()).isEqualTo(EditorialStatus.PUBLISHED);

    // Accessible in getReadingReaderData and creates progress
    var readerData = getReaderData.getReadingReaderData(reading.id());
    assertThat(readerData.readingId()).isEqualTo(reading.id());
    assertThat(progress.findByUserIdAndReadingId(testUser.id(), reading.id())).isPresent();
  }

  @Test
  @DisplayName(
      "B. PLATFORM ARCHIVED: hidden from catalog, recommendations, and accessible only with progress")
  void testPlatformArchivedLifecycle() {
    var reading =
        readings.save(
            new Reading(
                null,
                null,
                "Archived Article " + UUID.randomUUID(),
                "Ancient ruins remain hidden in the dense jungle.",
                "en",
                LocalDateTime.now(),
                ReadingOrigin.PLATFORM,
                EditorialLevel.B1,
                "HISTORY",
                "arch-cover",
                EditorialStatus.ARCHIVED));
    entityManager.flush();
    indexer.indexReading(reading.id(), reading.language(), reading.content());

    // NOT visible in listPlatformReadings
    var catalogPage = listPlatformReadings.listPlatformReadings(new PageRequest(0, 100));
    assertThat(catalogPage.content().stream().noneMatch(r -> r.id().equals(reading.id()))).isTrue();

    // NOT visible in recommendPlatformReadings
    var recPage = recommendReadings.recommendPlatformReadings(new PageRequest(0, 100));
    assertThat(recPage.content().stream().noneMatch(r -> r.readingId().equals(reading.id())))
        .isTrue();

    // getReading without progress -> throws ReadingNotFoundException
    assertThatThrownBy(() -> getReading.getReading(reading.id()))
        .isInstanceOf(ReadingNotFoundException.class);

    // getReadingReaderData without progress -> throws ReadingNotFoundException and does NOT create
    // progress
    assertThatThrownBy(() -> getReaderData.getReadingReaderData(reading.id()))
        .isInstanceOf(ReadingNotFoundException.class);
    assertThat(progress.findByUserIdAndReadingId(testUser.id(), reading.id())).isEmpty();

    // Now start progress for user (simulating prior progress before archiving)
    progress.startIfAbsent(testUser.id(), reading.id(), LocalDateTime.now());
    assertThat(progress.findByUserIdAndReadingId(testUser.id(), reading.id())).isPresent();

    // With progress: getReading succeeds
    var loaded = getReading.getReading(reading.id());
    assertThat(loaded.id()).isEqualTo(reading.id());

    // With progress: getReadingReaderData succeeds and preserves progress
    var readerData = getReaderData.getReadingReaderData(reading.id());
    assertThat(readerData.readingId()).isEqualTo(reading.id());
    assertThat(readerData.progressStatus()).isEqualTo(ReadingProgressStatus.IN_PROGRESS);

    // listContinueReading: includes archived reading when IN_PROGRESS
    var continuePage = continueReading.listContinueReading(new PageRequest(0, 10));
    assertThat(continuePage.content().stream().anyMatch(r -> r.readingId().equals(reading.id())))
        .isTrue();

    // Complete the reading: should no longer appear in listContinueReading
    progress.complete(testUser.id(), reading.id(), LocalDateTime.now());
    var continuePageAfter = continueReading.listContinueReading(new PageRequest(0, 10));
    assertThat(
            continuePageAfter.content().stream().noneMatch(r -> r.readingId().equals(reading.id())))
        .isTrue();
  }

  @Test
  @DisplayName("C. PLATFORM DRAFT: strictly inaccessible everywhere and never listed")
  void testPlatformDraftLifecycle() {
    var reading =
        readings.save(
            new Reading(
                null,
                null,
                "Draft Article " + UUID.randomUUID(),
                "Work in progress that nobody should see yet.",
                "en",
                LocalDateTime.now(),
                ReadingOrigin.PLATFORM,
                EditorialLevel.A2,
                "TECH",
                "draft-cover",
                EditorialStatus.DRAFT));
    entityManager.flush();
    indexer.indexReading(reading.id(), reading.language(), reading.content());

    // NOT in listPlatformReadings
    var catalogPage = listPlatformReadings.listPlatformReadings(new PageRequest(0, 100));
    assertThat(catalogPage.content().stream().noneMatch(r -> r.id().equals(reading.id()))).isTrue();

    // NOT in recommendPlatformReadings
    var recPage = recommendReadings.recommendPlatformReadings(new PageRequest(0, 100));
    assertThat(recPage.content().stream().noneMatch(r -> r.readingId().equals(reading.id())))
        .isTrue();

    // getReading throws ReadingNotFoundException
    assertThatThrownBy(() -> getReading.getReading(reading.id()))
        .isInstanceOf(ReadingNotFoundException.class);

    // getReadingReaderData throws ReadingNotFoundException and creates NO progress
    assertThatThrownBy(() -> getReaderData.getReadingReaderData(reading.id()))
        .isInstanceOf(ReadingNotFoundException.class);
    assertThat(progress.findByUserIdAndReadingId(testUser.id(), reading.id())).isEmpty();

    // If progress row was artificially inserted in DB, listContinueReading MUST still exclude DRAFT
    jdbc.update(
        "INSERT INTO reading_progress (id, user_id, reading_id, status, started_at) "
            + "VALUES (?, ?, ?, 'IN_PROGRESS', NOW())",
        UUID.randomUUID(),
        testUser.id(),
        reading.id());

    var continuePage = continueReading.listContinueReading(new PageRequest(0, 10));
    assertThat(continuePage.content().stream().noneMatch(r -> r.readingId().equals(reading.id())))
        .isTrue();

    // Quiz also throws ReadingNotFoundException
    assertThatThrownBy(() -> getQuiz.getQuiz(reading.id()))
        .isInstanceOf(ReadingNotFoundException.class);
  }

  @Test
  @DisplayName(
      "D. COLLECTIONS: listCollectionReadings excludes non-PUBLISHED and listCollections excludes empty collections")
  void testCollectionsEditorialFiltering() {
    var pubReading =
        readings.save(
            new Reading(
                null,
                null,
                "Pub in Coll",
                "Content",
                "en",
                LocalDateTime.now(),
                ReadingOrigin.PLATFORM,
                EditorialLevel.A1,
                "TEST",
                "cover",
                EditorialStatus.PUBLISHED));
    var archReading =
        readings.save(
            new Reading(
                null,
                null,
                "Arch in Coll",
                "Content",
                "en",
                LocalDateTime.now(),
                ReadingOrigin.PLATFORM,
                EditorialLevel.A1,
                "TEST",
                "cover",
                EditorialStatus.ARCHIVED));
    var draftReading =
        readings.save(
            new Reading(
                null,
                null,
                "Draft in Coll",
                "Content",
                "en",
                LocalDateTime.now(),
                ReadingOrigin.PLATFORM,
                EditorialLevel.A1,
                "TEST",
                "cover",
                EditorialStatus.DRAFT));
    entityManager.flush();

    // Create a collection with published, archived, draft
    UUID collId1 = UUID.randomUUID();
    String key1 = "coll-mixed-" + UUID.randomUUID().toString().substring(0, 8);
    jdbc.update(
        "INSERT INTO collections (id, key, display_name, description, display_order, active) VALUES (?, ?, ?, ?, ?, ?)",
        collId1,
        key1,
        "Mixed Collection",
        "Description",
        100,
        true);
    jdbc.update(
        "INSERT INTO reading_collections (collection_id, reading_id, display_order) VALUES (?, ?, ?)",
        collId1,
        pubReading.id(),
        1);
    jdbc.update(
        "INSERT INTO reading_collections (collection_id, reading_id, display_order) VALUES (?, ?, ?)",
        collId1,
        archReading.id(),
        2);
    jdbc.update(
        "INSERT INTO reading_collections (collection_id, reading_id, display_order) VALUES (?, ?, ?)",
        collId1,
        draftReading.id(),
        3);

    // Create a collection with ONLY archived & draft readings
    UUID collId2 = UUID.randomUUID();
    String key2 = "coll-empty-" + UUID.randomUUID().toString().substring(0, 8);
    jdbc.update(
        "INSERT INTO collections (id, key, display_name, description, display_order, active) VALUES (?, ?, ?, ?, ?, ?)",
        collId2,
        key2,
        "Empty Collection",
        "No published",
        101,
        true);
    jdbc.update(
        "INSERT INTO reading_collections (collection_id, reading_id, display_order) VALUES (?, ?, ?)",
        collId2,
        archReading.id(),
        1);
    jdbc.update(
        "INSERT INTO reading_collections (collection_id, reading_id, display_order) VALUES (?, ?, ?)",
        collId2,
        draftReading.id(),
        2);

    // listCollectionReadings: only returns PUBLISHED reading
    var collReadings = listCollectionReadings.listCollectionReadings(key1, new PageRequest(0, 10));
    assertThat(collReadings.content()).hasSize(1);
    assertThat(collReadings.content().get(0).readingId()).isEqualTo(pubReading.id());

    // listCollections: mixed collection is listed, empty collection is NOT listed
    var allActiveColls = listCollections.listCollections();
    assertThat(allActiveColls.stream().anyMatch(c -> c.key().equals(key1))).isTrue();
    assertThat(allActiveColls.stream().noneMatch(c -> c.key().equals(key2))).isTrue();
  }

  @Test
  @DisplayName(
      "E. COMPREHENSION QUIZ: ARCHIVED completed returns quiz, DRAFT throws ReadingNotFoundException")
  void testComprehensionQuizEditorialBehavior() {
    var archReading =
        readings.save(
            new Reading(
                null,
                null,
                "Archived with Quiz",
                "Content",
                "en",
                LocalDateTime.now(),
                ReadingOrigin.PLATFORM,
                EditorialLevel.B1,
                "TEST",
                "cover",
                EditorialStatus.ARCHIVED));
    var draftReading =
        readings.save(
            new Reading(
                null,
                null,
                "Draft with Quiz",
                "Content",
                "en",
                LocalDateTime.now(),
                ReadingOrigin.PLATFORM,
                EditorialLevel.B1,
                "TEST",
                "cover",
                EditorialStatus.DRAFT));
    entityManager.flush();

    // Progress completed for archReading
    progress.startIfAbsent(testUser.id(), archReading.id(), LocalDateTime.now());
    progress.complete(testUser.id(), archReading.id(), LocalDateTime.now());

    // Reading without questions returns available = false cleanly
    var quizView = getQuiz.getQuiz(archReading.id());
    assertThat(quizView.available()).isFalse();

    // DRAFT throws ReadingNotFoundException even if user completed it
    jdbc.update(
        "INSERT INTO reading_progress (id, user_id, reading_id, status, started_at, completed_at) VALUES (?, ?, ?, 'COMPLETED', NOW(), NOW())",
        UUID.randomUUID(),
        testUser.id(),
        draftReading.id());

    assertThatThrownBy(() -> getQuiz.getQuiz(draftReading.id()))
        .isInstanceOf(ReadingNotFoundException.class);
  }

  @Test
  @DisplayName("F. USER READINGS: editorialStatus is null and owner-only access")
  void testUserReadingBehavior() {
    var userReading =
        readings.save(
            new Reading(
                null,
                testUser,
                "My Personal Story",
                "Once upon a time in my room.",
                "en",
                LocalDateTime.now()));
    entityManager.flush();

    assertThat(userReading.editorialStatus()).isNull();
    assertThat(userReading.origin()).isEqualTo(ReadingOrigin.USER);

    // Owner accesses successfully
    var loaded = getReading.getReading(userReading.id());
    assertThat(loaded.id()).isEqualTo(userReading.id());

    // Other user cannot access -> ReadingNotFoundException
    authenticate(otherUser.id());
    assertThatThrownBy(() -> getReading.getReading(userReading.id()))
        .isInstanceOf(ReadingNotFoundException.class);

    // Never visible in listPlatformReadings or recommendations
    var catalogPage = listPlatformReadings.listPlatformReadings(new PageRequest(0, 100));
    assertThat(catalogPage.content().stream().noneMatch(r -> r.id().equals(userReading.id())))
        .isTrue();

    var recPage = recommendReadings.recommendPlatformReadings(new PageRequest(0, 100));
    assertThat(recPage.content().stream().noneMatch(r -> r.readingId().equals(userReading.id())))
        .isTrue();
  }
}
