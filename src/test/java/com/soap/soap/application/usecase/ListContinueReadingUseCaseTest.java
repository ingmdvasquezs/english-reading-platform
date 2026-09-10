package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.UserNotFoundException;
import com.soap.soap.application.model.ContinueReadingItem;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.ReadingProgressStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListContinueReadingUseCaseTest {
  @Mock private UserRepositoryPort users;
  @Mock private ReadingProgressRepositoryPort progress;
  @Mock private CurrentUserPort currentUser;

  @Test
  void delegatesOnePagedQueryForTheAuthenticatedUser() {
    var userId = UUID.randomUUID();
    var request = new PageRequest(1, 2);
    var expected =
        new PageResult<>(
            List.of(
                new ContinueReadingItem(
                    UUID.randomUUID(),
                    "Platform",
                    ReadingOrigin.PLATFORM,
                    ReadingProgressStatus.IN_PROGRESS,
                    "cover",
                    com.soap.soap.domain.model.EditorialLevel.B1,
                    "Science",
                    LocalDateTime.now())),
            1,
            2,
            5);
    when(currentUser.requireUserId()).thenReturn(userId);
    when(users.existsById(userId)).thenReturn(true);
    when(progress.findInProgressReadings(userId, request)).thenReturn(expected);

    assertThat(
            new ListContinueReadingUseCase(users, progress, currentUser)
                .listContinueReading(request))
        .isEqualTo(expected);
    verify(progress).findInProgressReadings(userId, request);
  }

  @Test
  void rejectsUnknownUserBeforeQueryingProgress() {
    var userId = UUID.randomUUID();
    var request = new PageRequest(0, 10);
    when(currentUser.requireUserId()).thenReturn(userId);
    when(users.existsById(userId)).thenReturn(false);

    assertThatThrownBy(
            () ->
                new ListContinueReadingUseCase(users, progress, currentUser)
                    .listContinueReading(request))
        .isInstanceOf(UserNotFoundException.class);
    verify(progress, never()).findInProgressReadings(userId, request);
  }
}
