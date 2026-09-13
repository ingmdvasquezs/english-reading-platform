package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.AuthenticationRequiredException;
import com.soap.soap.application.exception.ReadingNotFoundException;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.ReadingProgress;
import com.soap.soap.domain.model.ReadingProgressStatus;
import com.soap.soap.domain.model.User;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompleteReadingUseCaseTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-08-30T16:00:00Z"), ZoneOffset.UTC);
  @Mock private ReadingRepositoryPort readings;
  @Mock private ReadingProgressRepositoryPort progress;
  @Mock private CurrentUserPort currentUser;
  private CompleteReadingUseCase useCase;
  private UUID userId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    useCase =
        new CompleteReadingUseCase(
            readings,
            progress,
            new com.soap.soap.application.service.ReadingEditorialAccessPolicy(progress),
            currentUser,
            CLOCK);
  }

  @Test
  void completesAnOwnedReadingUsingTheAuthenticatedUser() {
    var reading = userReading(userId);
    var expected =
        ReadingProgress.completed(
            userId, reading.id(), LocalDateTime.now(CLOCK), LocalDateTime.now(CLOCK));
    when(currentUser.requireUserId()).thenReturn(userId);
    when(readings.findById(reading.id())).thenReturn(Optional.of(reading));
    when(progress.complete(userId, reading.id(), LocalDateTime.now(CLOCK))).thenReturn(expected);

    assertThat(useCase.completeReading(reading.id()).status())
        .isEqualTo(ReadingProgressStatus.COMPLETED);
    verify(progress).complete(userId, reading.id(), LocalDateTime.now(CLOCK));
  }

  @Test
  void permitsPlatformReadingButHidesForeignAndMissingReadings() {
    var platform =
        new Reading(
            UUID.randomUUID(),
            null,
            "Platform",
            "Text",
            "en",
            null,
            ReadingOrigin.PLATFORM,
            EditorialLevel.A1,
            "Test",
            com.soap.soap.domain.model.EditorialStatus.PUBLISHED);
    when(currentUser.requireUserId()).thenReturn(userId);
    when(readings.findById(platform.id())).thenReturn(Optional.of(platform));
    when(progress.complete(userId, platform.id(), LocalDateTime.now(CLOCK)))
        .thenReturn(
            ReadingProgress.completed(
                userId, platform.id(), LocalDateTime.now(CLOCK), LocalDateTime.now(CLOCK)));
    assertThat(useCase.completeReading(platform.id()).status())
        .isEqualTo(ReadingProgressStatus.COMPLETED);

    var foreign = userReading(UUID.randomUUID());
    when(readings.findById(foreign.id())).thenReturn(Optional.of(foreign));
    assertThatThrownBy(() -> useCase.completeReading(foreign.id()))
        .isInstanceOf(ReadingNotFoundException.class);

    var missing = UUID.randomUUID();
    when(readings.findById(missing)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.completeReading(missing))
        .isInstanceOf(ReadingNotFoundException.class);
    verify(progress, never()).complete(userId, foreign.id(), LocalDateTime.now(CLOCK));
  }

  @Test
  void requiresAuthenticationBeforeLoadingTheReading() {
    when(currentUser.requireUserId()).thenThrow(new AuthenticationRequiredException());

    assertThatThrownBy(() -> useCase.completeReading(UUID.randomUUID()))
        .isInstanceOf(AuthenticationRequiredException.class);
    verify(readings, never()).findById(org.mockito.ArgumentMatchers.any());
  }

  private Reading userReading(UUID ownerId) {
    return new Reading(
        UUID.randomUUID(),
        new User(ownerId, "Ada", ownerId + "@example.com"),
        "User",
        "Text",
        "en",
        null);
  }
}
