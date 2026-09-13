package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.AuthenticationRequiredException;
import com.soap.soap.application.exception.ReadingNotFoundException;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.User;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeleteReadingUseCaseTest {
  @Mock private ReadingRepositoryPort readings;
  @Mock private CurrentUserPort currentUser;
  private DeleteReadingUseCase useCase;
  private UUID userId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    useCase = new DeleteReadingUseCase(readings, currentUser);
  }

  @Test
  void deletesOnlyAnOwnedUserReading() {
    var reading = userReading(userId);
    when(currentUser.requireUserId()).thenReturn(userId);
    when(readings.findById(reading.id())).thenReturn(Optional.of(reading));

    useCase.deleteReading(reading.id());

    verify(readings).deleteById(reading.id());
  }

  @Test
  void hidesForeignMissingAndPlatformReadingsWithoutDeletingThem() {
    when(currentUser.requireUserId()).thenReturn(userId);
    var foreign = userReading(UUID.randomUUID());
    var missing = UUID.randomUUID();
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
    when(readings.findById(foreign.id())).thenReturn(Optional.of(foreign));
    when(readings.findById(missing)).thenReturn(Optional.empty());
    when(readings.findById(platform.id())).thenReturn(Optional.of(platform));

    assertThatThrownBy(() -> useCase.deleteReading(foreign.id()))
        .isInstanceOf(ReadingNotFoundException.class);
    assertThatThrownBy(() -> useCase.deleteReading(missing))
        .isInstanceOf(ReadingNotFoundException.class);
    assertThatThrownBy(() -> useCase.deleteReading(platform.id()))
        .isInstanceOf(ReadingNotFoundException.class);
    verify(readings, never()).deleteById(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void requiresAuthenticationBeforeLoadingOrDeleting() {
    when(currentUser.requireUserId()).thenThrow(new AuthenticationRequiredException());

    assertThatThrownBy(() -> useCase.deleteReading(UUID.randomUUID()))
        .isInstanceOf(AuthenticationRequiredException.class);
    verify(readings, never()).findById(org.mockito.ArgumentMatchers.any());
    verify(readings, never()).deleteById(org.mockito.ArgumentMatchers.any());
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
