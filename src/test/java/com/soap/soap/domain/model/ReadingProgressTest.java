package com.soap.soap.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReadingProgressTest {
  private final UUID userId = UUID.randomUUID();
  private final UUID readingId = UUID.randomUUID();
  private final LocalDateTime startedAt = LocalDateTime.parse("2026-08-30T10:00:00");

  @Test
  void inProgressRequiresStartedAtAndForbidsCompletedAt() {
    var progress = ReadingProgress.inProgress(userId, readingId, startedAt);

    assertThat(progress.status()).isEqualTo(ReadingProgressStatus.IN_PROGRESS);
    assertThat(progress.startedAt()).isEqualTo(startedAt);
    assertThat(progress.completedAt()).isNull();
    assertThatThrownBy(
            () ->
                new ReadingProgress(
                    null,
                    userId,
                    readingId,
                    ReadingProgressStatus.IN_PROGRESS,
                    startedAt,
                    startedAt))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void completionKeepsStartedAtAndRequiresCompletedAt() {
    var completedAt = startedAt.plusMinutes(5);
    var completed = ReadingProgress.inProgress(userId, readingId, startedAt).complete(completedAt);

    assertThat(completed.status()).isEqualTo(ReadingProgressStatus.COMPLETED);
    assertThat(completed.startedAt()).isEqualTo(startedAt);
    assertThat(completed.completedAt()).isEqualTo(completedAt);
    assertThatThrownBy(
            () ->
                new ReadingProgress(
                    null, userId, readingId, ReadingProgressStatus.COMPLETED, startedAt, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void completingAnAlreadyCompletedReadingIsIdempotent() {
    var completed =
        ReadingProgress.completed(userId, readingId, startedAt, startedAt.plusMinutes(5));

    assertThat(completed.complete(startedAt.plusHours(1))).isSameAs(completed);
  }
}
