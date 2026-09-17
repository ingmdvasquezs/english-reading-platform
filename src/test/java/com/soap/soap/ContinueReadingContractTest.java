package com.soap.soap;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.application.model.ContinueReadingItem;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.port.in.ListContinueReadingPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.domain.model.AccessTier;
import com.soap.soap.domain.model.AdaptationKind;
import com.soap.soap.domain.model.EditorialCategory;
import com.soap.soap.domain.model.EditorialContentType;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.EditorialRegion;
import com.soap.soap.domain.model.EditorialStatus;
import com.soap.soap.domain.model.LanguageTag;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.ReadingProgressStatus;
import com.soap.soap.domain.model.RightsStatus;
import com.soap.soap.domain.model.SourceKind;
import com.soap.soap.domain.model.User;
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
class ContinueReadingContractTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired private UserRepositoryPort users;
  @Autowired private ReadingRepositoryPort readings;
  @Autowired private ReadingProgressRepositoryPort progress;
  @Autowired private ListContinueReadingPort continueReading;
  @Autowired private CurrentUserPort currentUserPort;

  private User user;

  @BeforeEach
  void setUp() {
    user =
        users.save(
            new User(
                null,
                "Continue User",
                "continue-" + UUID.randomUUID() + "@example.com",
                "hash",
                LocalDateTime.now(),
                true,
                "cont-" + UUID.randomUUID().toString().substring(0, 8),
                30,
                "es",
                "en"));
    authenticate(user.id());
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  private void authenticate(UUID userId) {
    var jwt =
        Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .subject(userId.toString())
            .claim("sub", userId.toString())
            .build();
    var auth = new UsernamePasswordAuthenticationToken(jwt, null, List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @Test
  @DisplayName("1. ContinueReadingItem exposes shortDescription and progressPercentage")
  void continueReadingItemRecordContract() {
    UUID readingId = UUID.randomUUID();
    var inProgressItem =
        new ContinueReadingItem(
            readingId,
            "Title",
            ReadingOrigin.PLATFORM,
            ReadingProgressStatus.IN_PROGRESS,
            "cover",
            EditorialLevel.A1,
            "Daily Life & Relationships",
            LocalDateTime.now(),
            "Short description for card preview",
            null);

    assertThat(inProgressItem.shortDescription()).isEqualTo("Short description for card preview");
    assertThat(inProgressItem.progressPercentage()).isNull();

    var completedItem =
        new ContinueReadingItem(
            readingId,
            "Title",
            ReadingOrigin.PLATFORM,
            ReadingProgressStatus.COMPLETED,
            "cover",
            EditorialLevel.A1,
            "Daily Life & Relationships",
            LocalDateTime.now(),
            "Short description for card preview",
            100);

    assertThat(completedItem.progressPercentage()).isEqualTo(100);
  }

  @Test
  @DisplayName(
      "2. ListContinueReading returns shortDescription and progressPercentage=null for started-only IN_PROGRESS")
  void listContinueReadingReturnsShortDescriptionAndNullPercentage() {
    var reading =
        readings.save(
            new Reading(
                null,
                null,
                "The Lost City",
                "Explorers discovered a hidden temple deep in the jungle.",
                LanguageTag.of("en"),
                LocalDateTime.now(),
                ReadingOrigin.PLATFORM,
                EditorialLevel.B1,
                EditorialCategory.MYSTERY_AND_EXPLORATION.displayName(),
                "cover-city",
                EditorialStatus.PUBLISHED,
                "A captivating mystery in the heart of the rainforest.",
                EditorialContentType.MYTH,
                null,
                EditorialRegion.SOUTH_AMERICA,
                SourceKind.ORIGINAL_EDITORIAL,
                RightsStatus.ORIGINAL,
                AdaptationKind.ORIGINAL,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                AccessTier.FREE));

    // User starts reading without advancing to a part
    progress.startIfAbsent(user.id(), reading.id(), LocalDateTime.now());

    var result = continueReading.listContinueReading(new PageRequest(0, 10));
    assertThat(result.content()).isNotEmpty();

    var item =
        result.content().stream()
            .filter(r -> r.readingId().equals(reading.id()))
            .findFirst()
            .orElseThrow();

    assertThat(item.title()).isEqualTo("The Lost City");
    assertThat(item.shortDescription())
        .isEqualTo("A captivating mystery in the heart of the rainforest.");
    assertThat(item.progressStatus()).isEqualTo(ReadingProgressStatus.IN_PROGRESS);
    assertThat(item.progressPercentage()).isNull();
  }

  @Test
  @DisplayName(
      "3. ListContinueReading returns real calculated percentage for IN_PROGRESS with part ordinal")
  void listContinueReadingReturnsCalculatedPercentageForPlatformReading() {
    // Reading with 3 paragraphs of 90, 80, 110 words = 2 parts total
    var p1 =
        java.util.stream.IntStream.range(0, 90)
                .mapToObj(i -> "alpha" + i)
                .collect(java.util.stream.Collectors.joining(" "))
            + ".";
    var p2 =
        java.util.stream.IntStream.range(0, 80)
                .mapToObj(i -> "beta" + i)
                .collect(java.util.stream.Collectors.joining(" "))
            + ".";
    var p3 =
        java.util.stream.IntStream.range(0, 110)
                .mapToObj(i -> "gamma" + i)
                .collect(java.util.stream.Collectors.joining(" "))
            + ".";
    var content = String.join("\n\n", p1, p2, p3);

    var reading =
        readings.save(
            new Reading(
                null,
                null,
                "The Two Part Legend",
                content,
                LanguageTag.of("en"),
                LocalDateTime.now(),
                ReadingOrigin.PLATFORM,
                EditorialLevel.B1,
                EditorialCategory.MYSTERY_AND_EXPLORATION.displayName(),
                "cover-two-part",
                EditorialStatus.PUBLISHED,
                "A tale with two parts.",
                EditorialContentType.MYTH,
                null,
                EditorialRegion.SOUTH_AMERICA,
                SourceKind.ORIGINAL_EDITORIAL,
                RightsStatus.ORIGINAL,
                AdaptationKind.ORIGINAL,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                AccessTier.FREE));

    progress.startIfAbsent(user.id(), reading.id(), LocalDateTime.now());
    // User moves to part 1 of 2 -> 50%
    progress.updatePosition(user.id(), reading.id(), 1, 1);

    var result = continueReading.listContinueReading(new PageRequest(0, 10));
    var item =
        result.content().stream()
            .filter(r -> r.readingId().equals(reading.id()))
            .findFirst()
            .orElseThrow();

    assertThat(item.progressStatus()).isEqualTo(ReadingProgressStatus.IN_PROGRESS);
    assertThat(item.progressPercentage()).isEqualTo(50);

    // When on the final part (part 2 of 2) while IN_PROGRESS, percentage is capped at 99
    progress.updatePosition(user.id(), reading.id(), 2, 1);
    var updatedResult = continueReading.listContinueReading(new PageRequest(0, 10));
    var updatedItem =
        updatedResult.content().stream()
            .filter(r -> r.readingId().equals(reading.id()))
            .findFirst()
            .orElseThrow();
    assertThat(updatedItem.progressPercentage()).isEqualTo(99);
  }

  @Test
  @DisplayName("4. ListContinueReading supports USER origin readings with real progress")
  void listContinueReadingSupportsUserReadings() {
    var p1 =
        java.util.stream.IntStream.range(0, 90)
                .mapToObj(i -> "userword" + i)
                .collect(java.util.stream.Collectors.joining(" "))
            + ".";
    var p2 =
        java.util.stream.IntStream.range(0, 80)
                .mapToObj(i -> "userword" + i)
                .collect(java.util.stream.Collectors.joining(" "))
            + ".";
    var p3 =
        java.util.stream.IntStream.range(0, 110)
                .mapToObj(i -> "userword" + i)
                .collect(java.util.stream.Collectors.joining(" "))
            + ".";
    var content = String.join("\n\n", p1, p2, p3);

    var userReading =
        readings.save(
            new Reading(
                null,
                user,
                "My Personal Story",
                content,
                LanguageTag.of("en"),
                LocalDateTime.now(),
                ReadingOrigin.USER,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));

    progress.startIfAbsent(user.id(), userReading.id(), LocalDateTime.now());
    progress.updatePosition(user.id(), userReading.id(), 1, 1);

    var result = continueReading.listContinueReading(new PageRequest(0, 10));
    var item =
        result.content().stream()
            .filter(r -> r.readingId().equals(userReading.id()))
            .findFirst()
            .orElseThrow();

    assertThat(item.origin()).isEqualTo(ReadingOrigin.USER);
    assertThat(item.title()).isEqualTo("My Personal Story");
    assertThat(item.progressPercentage()).isEqualTo(50);
  }
}
