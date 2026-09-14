package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.PlatformReadingHistoryItem;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.ReadingProgressStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListPlatformReadingHistoryUseCaseTest {
  @Mock private ReadingProgressRepositoryPort progress;
  @Mock private CurrentUserPort currentUser;

  @Test
  void delegatesOnePagedQueryForTheAuthenticatedUser() {
    var userId = UUID.randomUUID();
    var request = new PageRequest(0, 10);
    var expected =
        new PageResult<>(
            List.of(
                new PlatformReadingHistoryItem(
                    UUID.randomUUID(),
                    "Platform Story",
                    EditorialLevel.B1,
                    "Fiction",
                    "covers/story.jpg",
                    ReadingProgressStatus.IN_PROGRESS)),
            0,
            10,
            1);
    when(currentUser.requireUserId()).thenReturn(userId);
    when(progress.findPlatformReadingHistory(userId, request)).thenReturn(expected);

    var useCase = new ListPlatformReadingHistoryUseCase(progress, currentUser);
    assertThat(useCase.listPlatformReadingHistory(request)).isEqualTo(expected);
    verify(progress).findPlatformReadingHistory(userId, request);
  }

  @Test
  void rejectsNullPageRequest() {
    var userId = UUID.randomUUID();
    when(currentUser.requireUserId()).thenReturn(userId);

    var useCase = new ListPlatformReadingHistoryUseCase(progress, currentUser);
    assertThatThrownBy(() -> useCase.listPlatformReadingHistory(null))
        .isInstanceOf(InvalidApplicationArgumentException.class)
        .hasMessage("Page request must not be null");
  }

  @Test
  void modelRejectsNullProgressStatus() {
    var readingId = UUID.randomUUID();
    assertThatThrownBy(
            () ->
                new PlatformReadingHistoryItem(
                    readingId, "Title", EditorialLevel.A1, "Category", null, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("progressStatus must not be null");
  }
}
