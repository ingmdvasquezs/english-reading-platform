package com.soap.soap.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentProgressTest {

  private final UUID userId = UUID.randomUUID();
  private final UUID documentId = UUID.randomUUID();
  private final UUID firstUnitId = UUID.randomUUID();
  private final LocalDateTime startedAt = LocalDateTime.parse("2026-09-07T10:00:00");

  @Test
  void startsAndAdvancesWithoutChangingStartTime() {
    var started = DocumentProgress.start(userId, documentId, firstUnitId, startedAt);
    var nextUnit = UUID.randomUUID();
    var advanced = started.moveTo(nextUnit, startedAt.plusMinutes(2));

    assertThat(started.status()).isEqualTo(DocumentProgressStatus.IN_PROGRESS);
    assertThat(started.startedAt()).isEqualTo(started.lastReadAt());
    assertThat(advanced.currentUnitId()).isEqualTo(nextUnit);
    assertThat(advanced.startedAt()).isEqualTo(startedAt);
    assertThat(advanced.lastReadAt()).isEqualTo(startedAt.plusMinutes(2));
  }

  @Test
  void repeatingExactlyTheSameUpdateIsIdempotent() {
    var progress = DocumentProgress.start(userId, documentId, firstUnitId, startedAt);

    assertThat(progress.moveTo(firstUnitId, startedAt)).isSameAs(progress);
  }

  @Test
  void completionIsTerminalAndPreservesItsOriginalTimestampWhenReopened() {
    var lastUnit = UUID.randomUUID();
    var completedAt = startedAt.plusMinutes(10);
    var completed =
        DocumentProgress.start(userId, documentId, firstUnitId, startedAt)
            .complete(lastUnit, completedAt);

    assertThat(completed.status()).isEqualTo(DocumentProgressStatus.COMPLETED);
    assertThat(completed.currentUnitId()).isEqualTo(lastUnit);
    assertThat(completed.completedAt()).isEqualTo(completedAt);
    assertThat(completed.complete(firstUnitId, completedAt.plusHours(1))).isSameAs(completed);

    var reopened = completed.moveTo(firstUnitId, completedAt.plusHours(1));
    assertThat(reopened.status()).isEqualTo(DocumentProgressStatus.COMPLETED);
    assertThat(reopened.completedAt()).isEqualTo(completedAt);
    assertThat(reopened.currentUnitId()).isEqualTo(firstUnitId);
  }

  @Test
  void rejectsTimeTravelAndInvalidStatusTimestampCombinations() {
    assertThatThrownBy(
            () ->
                DocumentProgress.start(userId, documentId, firstUnitId, startedAt)
                    .moveTo(firstUnitId, startedAt.minusSeconds(1)))
        .hasMessageContaining("precede lastReadAt");
    assertThatThrownBy(
            () ->
                new DocumentProgress(
                    null,
                    userId,
                    documentId,
                    firstUnitId,
                    DocumentProgressStatus.COMPLETED,
                    startedAt,
                    startedAt,
                    null,
                    null))
        .hasMessageContaining("completedAt");
  }
}
