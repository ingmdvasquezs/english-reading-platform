package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.command.UpdateReadingProgressCommand;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.ReadingNotFoundException;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.domain.model.Reading;
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
class UpdateReadingProgressUseCaseTest {
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-09-09T12:00:00Z"), ZoneOffset.UTC);
  @Mock ReadingRepositoryPort readings;
  @Mock ReadingProgressRepositoryPort progress;
  @Mock CurrentUserPort currentUser;
  private UpdateReadingProgressUseCase useCase;
  private UUID userId;
  private Reading reading;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    reading =
        new Reading(
            UUID.randomUUID(),
            new User(userId, "Ada", "ada@example.com"),
            "Title",
            "Text",
            "en",
            null);
    useCase = new UpdateReadingProgressUseCase(readings, progress, currentUser, CLOCK);
  }

  @Test
  void persistsExactPartForTheAuthenticatedUser() {
    authenticateAccessibleReading();
    var started = ReadingProgress.inProgress(userId, reading.id(), LocalDateTime.now(CLOCK));
    var positioned = started.withPosition(8, 1);
    when(progress.startIfAbsent(userId, reading.id(), LocalDateTime.now(CLOCK)))
        .thenReturn(started);
    when(progress.updatePosition(userId, reading.id(), 8, 1)).thenReturn(positioned);

    var result =
        useCase.updateReadingProgress(
            new UpdateReadingProgressCommand(
                reading.id(), ReadingProgressStatus.IN_PROGRESS, 8, 1));

    assertThat(result.currentPartOrdinal()).isEqualTo(8);
    assertThat(result.paginationVersion()).isEqualTo(1);
    verify(progress).updatePosition(userId, reading.id(), 8, 1);
  }

  @Test
  void legacyRequestPreservesExistingPositionAndCompletionPreservesItToo() {
    authenticateAccessibleReading();
    var existing =
        ReadingProgress.inProgress(userId, reading.id(), LocalDateTime.now(CLOCK))
            .withPosition(7, 1);
    when(progress.startIfAbsent(userId, reading.id(), LocalDateTime.now(CLOCK)))
        .thenReturn(existing);
    assertThat(
            useCase.updateReadingProgress(
                new UpdateReadingProgressCommand(
                    reading.id(), ReadingProgressStatus.IN_PROGRESS, null, null)))
        .isEqualTo(existing);
    verify(progress, never()).updatePosition(userId, reading.id(), 7, 1);

    var completed = existing.complete(LocalDateTime.now(CLOCK));
    when(progress.complete(userId, reading.id(), LocalDateTime.now(CLOCK))).thenReturn(completed);
    assertThat(
            useCase
                .updateReadingProgress(
                    new UpdateReadingProgressCommand(
                        reading.id(), ReadingProgressStatus.COMPLETED, null, null))
                .currentPartOrdinal())
        .isEqualTo(7);
  }

  @Test
  void rejectsPartialAndNonPositivePairsBeforeAuthentication() {
    assertThatThrownBy(
            () ->
                useCase.updateReadingProgress(
                    new UpdateReadingProgressCommand(
                        reading.id(), ReadingProgressStatus.IN_PROGRESS, 1, null)))
        .isInstanceOf(InvalidApplicationArgumentException.class);
    assertThatThrownBy(
            () ->
                useCase.updateReadingProgress(
                    new UpdateReadingProgressCommand(
                        reading.id(), ReadingProgressStatus.IN_PROGRESS, 0, 1)))
        .isInstanceOf(InvalidApplicationArgumentException.class);
    assertThatThrownBy(
            () ->
                useCase.updateReadingProgress(
                    new UpdateReadingProgressCommand(
                        reading.id(), ReadingProgressStatus.IN_PROGRESS, 1, 0)))
        .isInstanceOf(InvalidApplicationArgumentException.class);
    assertThatThrownBy(
            () ->
                useCase.updateReadingProgress(
                    new UpdateReadingProgressCommand(
                        reading.id(), ReadingProgressStatus.IN_PROGRESS, -1, 1)))
        .isInstanceOf(InvalidApplicationArgumentException.class);
    assertThatThrownBy(
            () ->
                useCase.updateReadingProgress(
                    new UpdateReadingProgressCommand(
                        reading.id(), ReadingProgressStatus.IN_PROGRESS, 1, -1)))
        .isInstanceOf(InvalidApplicationArgumentException.class);
    verify(currentUser, never()).requireUserId();
  }

  @Test
  void cannotUpdateAnotherUsersReading() {
    when(currentUser.requireUserId()).thenReturn(userId);
    var foreign =
        new Reading(
            reading.id(),
            new User(UUID.randomUUID(), "Other", "o@example.com"),
            "Title",
            "Text",
            "en",
            null);
    when(readings.findById(reading.id())).thenReturn(Optional.of(foreign));

    assertThatThrownBy(
            () ->
                useCase.updateReadingProgress(
                    new UpdateReadingProgressCommand(
                        reading.id(), ReadingProgressStatus.IN_PROGRESS, 1, 1)))
        .isInstanceOf(ReadingNotFoundException.class);
    verify(progress, never()).updatePosition(userId, reading.id(), 1, 1);
  }

  private void authenticateAccessibleReading() {
    when(currentUser.requireUserId()).thenReturn(userId);
    when(readings.findById(reading.id())).thenReturn(Optional.of(reading));
  }
}
