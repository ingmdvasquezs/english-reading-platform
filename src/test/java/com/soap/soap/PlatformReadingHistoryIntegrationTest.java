package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.port.in.GetReadingReaderDataPort;
import com.soap.soap.application.port.in.ListContinueReadingPort;
import com.soap.soap.application.port.in.ListPlatformReadingHistoryPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
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
class PlatformReadingHistoryIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private ListPlatformReadingHistoryPort historyPort;
  @Autowired private ListContinueReadingPort continueReadingPort;
  @Autowired private GetReadingReaderDataPort readerDataPort;
  @Autowired private ReadingRepositoryPort readings;
  @Autowired private ReadingProgressRepositoryPort progress;
  @Autowired private UserRepositoryPort users;
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
                "history_user_" + UUID.randomUUID(),
                "history_" + UUID.randomUUID() + "@example.com",
                "hash",
                null));
    otherUser =
        users.save(
            new User(
                null,
                "history_other_" + UUID.randomUUID(),
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

  private Reading createPlatformReading(
      String title, EditorialStatus status, EditorialLevel level) {
    var r =
        readings.save(
            new Reading(
                null,
                null,
                title,
                "The quick brown fox jumps over the lazy dog in " + title,
                "en",
                LocalDateTime.now(),
                ReadingOrigin.PLATFORM,
                level,
                "Culture, Arts & Fiction",
                "covers/" + title + ".jpg",
                status));
    entityManager.flush();
    return r;
  }

  private Reading createUserReading(String title, User owner) {
    var r =
        readings.save(
            new Reading(
                null,
                owner,
                title,
                "User created text content for " + title,
                "en",
                LocalDateTime.now()));
    entityManager.flush();
    return r;
  }

  @Test
  @DisplayName("A. PUBLISHED + IN_PROGRESS appears in history")
  void testPublishedInProgressAppearsInHistory() {
    var reading =
        createPlatformReading(
            "Pub InProgress " + UUID.randomUUID(), EditorialStatus.PUBLISHED, EditorialLevel.A1);
    progress.startIfAbsent(testUser.id(), reading.id(), LocalDateTime.now());
    entityManager.flush();

    var history = historyPort.listPlatformReadingHistory(new PageRequest(0, 50));
    var match =
        history.content().stream().filter(i -> i.readingId().equals(reading.id())).findFirst();

    assertThat(match).isPresent();
    var item = match.get();
    assertThat(item.title()).isEqualTo(reading.title());
    assertThat(item.editorialLevel()).isEqualTo(EditorialLevel.A1);
    assertThat(item.category()).isEqualTo("Culture, Arts & Fiction");
    assertThat(item.coverKey()).isEqualTo(reading.coverKey());
    assertThat(item.progressStatus()).isEqualTo(ReadingProgressStatus.IN_PROGRESS);
  }

  @Test
  @DisplayName("B. PUBLISHED + COMPLETED appears in history")
  void testPublishedCompletedAppearsInHistory() {
    var reading =
        createPlatformReading(
            "Pub Completed " + UUID.randomUUID(), EditorialStatus.PUBLISHED, EditorialLevel.A2);
    progress.startIfAbsent(testUser.id(), reading.id(), LocalDateTime.now().minusHours(1));
    progress.complete(testUser.id(), reading.id(), LocalDateTime.now());
    entityManager.flush();

    var history = historyPort.listPlatformReadingHistory(new PageRequest(0, 50));
    var match =
        history.content().stream().filter(i -> i.readingId().equals(reading.id())).findFirst();

    assertThat(match).isPresent();
    assertThat(match.get().progressStatus()).isEqualTo(ReadingProgressStatus.COMPLETED);
  }

  @Test
  @DisplayName("C. ARCHIVED + IN_PROGRESS appears in history")
  void testArchivedInProgressAppearsInHistory() {
    var reading =
        createPlatformReading(
            "Arch InProgress " + UUID.randomUUID(), EditorialStatus.ARCHIVED, EditorialLevel.B1);
    jdbc.update(
        "INSERT INTO reading_progress(id, user_id, reading_id, status, started_at) VALUES (?, ?, ?, 'IN_PROGRESS', ?)",
        UUID.randomUUID(),
        testUser.id(),
        reading.id(),
        LocalDateTime.now().minusHours(2));
    entityManager.flush();

    var history = historyPort.listPlatformReadingHistory(new PageRequest(0, 50));
    var match =
        history.content().stream().filter(i -> i.readingId().equals(reading.id())).findFirst();

    assertThat(match).isPresent();
    assertThat(match.get().progressStatus()).isEqualTo(ReadingProgressStatus.IN_PROGRESS);
    assertThat(match.get().editorialLevel()).isEqualTo(EditorialLevel.B1);
  }

  @Test
  @DisplayName("D. ARCHIVED + COMPLETED appears in history")
  void testArchivedCompletedAppearsInHistory() {
    var reading =
        createPlatformReading(
            "Arch Completed " + UUID.randomUUID(), EditorialStatus.ARCHIVED, EditorialLevel.B2);
    jdbc.update(
        "INSERT INTO reading_progress(id, user_id, reading_id, status, started_at, completed_at) VALUES (?, ?, ?, 'COMPLETED', ?, ?)",
        UUID.randomUUID(),
        testUser.id(),
        reading.id(),
        LocalDateTime.now().minusHours(3),
        LocalDateTime.now().minusHours(1));
    entityManager.flush();

    var history = historyPort.listPlatformReadingHistory(new PageRequest(0, 50));
    var match =
        history.content().stream().filter(i -> i.readingId().equals(reading.id())).findFirst();

    assertThat(match).isPresent();
    assertThat(match.get().progressStatus()).isEqualTo(ReadingProgressStatus.COMPLETED);
    assertThat(match.get().editorialLevel()).isEqualTo(EditorialLevel.B2);
  }

  @Test
  @DisplayName("E. DRAFT + artificial progress DOES NOT appear in history")
  void testDraftWithProgressIsExcludedFromHistory() {
    var reading =
        createPlatformReading(
            "Draft With Progress " + UUID.randomUUID(), EditorialStatus.DRAFT, EditorialLevel.C1);
    jdbc.update(
        "INSERT INTO reading_progress(id, user_id, reading_id, status, started_at) VALUES (?, ?, ?, 'IN_PROGRESS', ?)",
        UUID.randomUUID(),
        testUser.id(),
        reading.id(),
        LocalDateTime.now());
    entityManager.flush();

    var history = historyPort.listPlatformReadingHistory(new PageRequest(0, 100));
    assertThat(history.content().stream().anyMatch(i -> i.readingId().equals(reading.id())))
        .isFalse();
  }

  @Test
  @DisplayName("F. PUBLISHED without progress DOES NOT appear in history")
  void testPublishedWithoutProgressIsExcludedFromHistory() {
    var reading =
        createPlatformReading(
            "Unstarted " + UUID.randomUUID(), EditorialStatus.PUBLISHED, EditorialLevel.A1);
    entityManager.flush();

    var history = historyPort.listPlatformReadingHistory(new PageRequest(0, 100));
    assertThat(history.content().stream().anyMatch(i -> i.readingId().equals(reading.id())))
        .isFalse();
  }

  @Test
  @DisplayName("G. USER reading with progress DOES NOT appear in history")
  void testUserReadingWithProgressIsExcludedFromHistory() {
    var reading = createUserReading("Personal Note " + UUID.randomUUID(), testUser);
    progress.startIfAbsent(testUser.id(), reading.id(), LocalDateTime.now());
    entityManager.flush();

    var history = historyPort.listPlatformReadingHistory(new PageRequest(0, 100));
    assertThat(history.content().stream().anyMatch(i -> i.readingId().equals(reading.id())))
        .isFalse();
  }

  @Test
  @DisplayName("H. Other user's progress DOES NOT appear in history")
  void testUserIsolation() {
    var reading =
        createPlatformReading(
            "Other User Reading " + UUID.randomUUID(),
            EditorialStatus.PUBLISHED,
            EditorialLevel.A2);
    progress.startIfAbsent(otherUser.id(), reading.id(), LocalDateTime.now());
    entityManager.flush();

    var history = historyPort.listPlatformReadingHistory(new PageRequest(0, 100));
    assertThat(history.content().stream().anyMatch(i -> i.readingId().equals(reading.id())))
        .isFalse();
  }

  @Test
  @DisplayName(
      "I & J. Ordering: IN_PROGRESS before COMPLETED, with deterministic secondary ordering")
  void testHistoryOrdering() {
    // Delete any prior progress for this user in this test to have exact ordering control
    jdbc.update("DELETE FROM reading_progress WHERE user_id = ?", testUser.id());

    var now = LocalDateTime.now();

    // 1. in_progress started earlier (10:00)
    var r1 =
        createPlatformReading(
            "Reading 1 " + UUID.randomUUID(), EditorialStatus.PUBLISHED, EditorialLevel.A1);
    jdbc.update(
        "INSERT INTO reading_progress(id, user_id, reading_id, status, started_at) VALUES (?, ?, ?, 'IN_PROGRESS', ?)",
        UUID.randomUUID(),
        testUser.id(),
        r1.id(),
        now.minusHours(4));

    // 2. in_progress started later (11:00) -> should be #1
    var r2 =
        createPlatformReading(
            "Reading 2 " + UUID.randomUUID(), EditorialStatus.PUBLISHED, EditorialLevel.A2);
    jdbc.update(
        "INSERT INTO reading_progress(id, user_id, reading_id, status, started_at) VALUES (?, ?, ?, 'IN_PROGRESS', ?)",
        UUID.randomUUID(),
        testUser.id(),
        r2.id(),
        now.minusHours(3));

    // 3. completed finished earlier (12:00)
    var r3 =
        createPlatformReading(
            "Reading 3 " + UUID.randomUUID(), EditorialStatus.ARCHIVED, EditorialLevel.B1);
    jdbc.update(
        "INSERT INTO reading_progress(id, user_id, reading_id, status, started_at, completed_at) VALUES (?, ?, ?, 'COMPLETED', ?, ?)",
        UUID.randomUUID(),
        testUser.id(),
        r3.id(),
        now.minusHours(5),
        now.minusHours(2));

    // 4. completed finished later (13:00) -> should be ahead of r3
    var r4 =
        createPlatformReading(
            "Reading 4 " + UUID.randomUUID(), EditorialStatus.PUBLISHED, EditorialLevel.B2);
    jdbc.update(
        "INSERT INTO reading_progress(id, user_id, reading_id, status, started_at, completed_at) VALUES (?, ?, ?, 'COMPLETED', ?, ?)",
        UUID.randomUUID(),
        testUser.id(),
        r4.id(),
        now.minusHours(5),
        now.minusHours(1));

    entityManager.flush();

    var history = historyPort.listPlatformReadingHistory(new PageRequest(0, 10));
    assertThat(history.content()).hasSize(4);

    // IN_PROGRESS first, sorted by started_at DESC
    assertThat(history.content().get(0).readingId()).isEqualTo(r2.id());
    assertThat(history.content().get(1).readingId()).isEqualTo(r1.id());

    // COMPLETED after, sorted by completed_at DESC
    assertThat(history.content().get(2).readingId()).isEqualTo(r4.id());
    assertThat(history.content().get(3).readingId()).isEqualTo(r3.id());
  }

  @Test
  @DisplayName("K. Real database pagination: page count, totalElements, and slice boundaries")
  void testDatabasePagination() {
    jdbc.update("DELETE FROM reading_progress WHERE user_id = ?", testUser.id());
    var now = LocalDateTime.now();

    for (int i = 0; i < 5; i++) {
      var r =
          createPlatformReading(
              "Pagination " + i + " " + UUID.randomUUID(),
              EditorialStatus.PUBLISHED,
              EditorialLevel.A1);
      jdbc.update(
          "INSERT INTO reading_progress(id, user_id, reading_id, status, started_at) VALUES (?, ?, ?, 'IN_PROGRESS', ?)",
          UUID.randomUUID(),
          testUser.id(),
          r.id(),
          now.minusMinutes(i));
    }
    entityManager.flush();

    // Page 0 (size 2)
    var page0 = historyPort.listPlatformReadingHistory(new PageRequest(0, 2));
    assertThat(page0.page()).isEqualTo(0);
    assertThat(page0.size()).isEqualTo(2);
    assertThat(page0.totalElements()).isEqualTo(5);
    assertThat(page0.totalPages()).isEqualTo(3);
    assertThat(page0.content()).hasSize(2);

    // Page 1 (size 2)
    var page1 = historyPort.listPlatformReadingHistory(new PageRequest(1, 2));
    assertThat(page1.page()).isEqualTo(1);
    assertThat(page1.content()).hasSize(2);
    assertThat(page1.content().get(0).readingId()).isNotEqualTo(page0.content().get(0).readingId());
    assertThat(page1.content().get(1).readingId()).isNotEqualTo(page0.content().get(1).readingId());

    // Page 2 (size 2) -> remainder of 1
    var page2 = historyPort.listPlatformReadingHistory(new PageRequest(2, 2));
    assertThat(page2.page()).isEqualTo(2);
    assertThat(page2.content()).hasSize(1);

    // Page 3 (size 2) -> beyond total
    var page3 = historyPort.listPlatformReadingHistory(new PageRequest(3, 2));
    assertThat(page3.page()).isEqualTo(3);
    assertThat(page3.totalElements()).isEqualTo(5);
    assertThat(page3.content()).isEmpty();
  }

  @Test
  @DisplayName(
      "L, M, N. No regression in continue reading, and archived reading is accessible from reader")
  void testNoRegressionAndArchivedAccessibility() {
    jdbc.update("DELETE FROM reading_progress WHERE user_id = ?", testUser.id());
    var now = LocalDateTime.now();

    var publishedInProgress =
        createPlatformReading(
            "P1 " + UUID.randomUUID(), EditorialStatus.PUBLISHED, EditorialLevel.A1);
    progress.startIfAbsent(testUser.id(), publishedInProgress.id(), now.minusMinutes(10));

    var publishedCompleted =
        createPlatformReading(
            "P2 " + UUID.randomUUID(), EditorialStatus.PUBLISHED, EditorialLevel.A2);
    progress.startIfAbsent(testUser.id(), publishedCompleted.id(), now.minusMinutes(30));
    progress.complete(testUser.id(), publishedCompleted.id(), now.minusMinutes(5));

    var archivedInProgress =
        createPlatformReading(
            "A1 " + UUID.randomUUID(), EditorialStatus.ARCHIVED, EditorialLevel.B1);
    jdbc.update(
        "INSERT INTO reading_progress(id, user_id, reading_id, status, started_at) VALUES (?, ?, ?, 'IN_PROGRESS', ?)",
        UUID.randomUUID(),
        testUser.id(),
        archivedInProgress.id(),
        now.minusMinutes(20));

    var archivedCompleted =
        createPlatformReading(
            "A2 " + UUID.randomUUID(), EditorialStatus.ARCHIVED, EditorialLevel.B2);
    jdbc.update(
        "INSERT INTO reading_progress(id, user_id, reading_id, status, started_at, completed_at) VALUES (?, ?, ?, 'COMPLETED', ?, ?)",
        UUID.randomUUID(),
        testUser.id(),
        archivedCompleted.id(),
        now.minusMinutes(40),
        now.minusMinutes(15));

    entityManager.flush();

    // Verify history includes all 4
    var history = historyPort.listPlatformReadingHistory(new PageRequest(0, 10));
    assertThat(history.totalElements()).isEqualTo(4);

    // Criteria M: listContinueReading MUST ONLY include IN_PROGRESS (P1 and A1), never COMPLETED
    // (P2 and A2)
    var continuePage = continueReadingPort.listContinueReading(new PageRequest(0, 10));
    assertThat(continuePage.totalElements()).isEqualTo(2);
    var continueIds =
        continuePage.content().stream()
            .map(com.soap.soap.application.model.ContinueReadingItem::readingId)
            .toList();
    assertThat(continueIds)
        .containsExactlyInAnyOrder(publishedInProgress.id(), archivedInProgress.id());

    // Criteria N: ARCHIVED readings present in history are accessible to this user in reader
    var readerArchivedInProgress = readerDataPort.getReadingReaderData(archivedInProgress.id());
    assertThat(readerArchivedInProgress).isNotNull();
    assertThat(readerArchivedInProgress.readingId()).isEqualTo(archivedInProgress.id());

    var readerArchivedCompleted = readerDataPort.getReadingReaderData(archivedCompleted.id());
    assertThat(readerArchivedCompleted).isNotNull();
    assertThat(readerArchivedCompleted.readingId()).isEqualTo(archivedCompleted.id());
  }
}
