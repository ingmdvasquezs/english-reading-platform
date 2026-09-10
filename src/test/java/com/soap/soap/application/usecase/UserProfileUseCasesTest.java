package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.command.UpdateMyProfileCommand;
import com.soap.soap.application.exception.AliasAlreadyInUseException;
import com.soap.soap.application.exception.AuthenticationRequiredException;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.model.InputLimits;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.domain.model.User;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserProfileUseCasesTest {
  @Mock private CurrentUserPort currentUser;
  @Mock private UserRepositoryPort users;

  private UUID userId;
  private User user;
  private UpdateMyProfileUseCase update;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    user =
        new User(
            userId,
            "Ada",
            "ada@example.com",
            "secret-hash",
            LocalDateTime.now(),
            true,
            null,
            null,
            null,
            "en");
    update = new UpdateMyProfileUseCase(currentUser, users, InputLimits.defaults());
  }

  @Test
  void getsOnlyTheAuthenticatedUsersProfile() {
    when(currentUser.requireUserId()).thenReturn(userId);
    when(users.findById(userId)).thenReturn(Optional.of(user));

    var profile = new GetMyProfileUseCase(currentUser, users).getMyProfile();

    assertThat(profile.email()).isEqualTo("ada@example.com");
    verify(users).findById(userId);
  }

  @Test
  void bothOperationsRequireAuthentication() {
    when(currentUser.requireUserId()).thenThrow(new AuthenticationRequiredException());

    assertThatThrownBy(() -> new GetMyProfileUseCase(currentUser, users).getMyProfile())
        .isInstanceOf(AuthenticationRequiredException.class);
    assertThatThrownBy(
            () -> update.updateMyProfile(new UpdateMyProfileCommand("Ada", null, null, null, "en")))
        .isInstanceOf(AuthenticationRequiredException.class);
    verify(users, never()).save(any());
  }

  @Test
  void updatesAndNormalizesProfileWithoutChangingAccountState() {
    arrangeUser();
    when(users.existsByAliasIgnoreCaseAndIdNot("MarlonV", userId)).thenReturn(false);
    when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var result =
        update.updateMyProfile(
            new UpdateMyProfileCommand(" Marlon ", " MarlonV ", 37, "ES-co", "EN"));

    var saved = ArgumentCaptor.forClass(User.class);
    verify(users).save(saved.capture());
    assertThat(result.name()).isEqualTo("Marlon");
    assertThat(result.alias()).isEqualTo("MarlonV");
    assertThat(result.nativeLanguage()).isEqualTo("es-CO");
    assertThat(saved.getValue().email()).isEqualTo(user.email());
    assertThat(saved.getValue().passwordHash()).isEqualTo(user.passwordHash());
    assertThat(saved.getValue().onboardingCompleted()).isTrue();
  }

  @Test
  void nullAliasAndAgeExplicitlyClearThem() {
    user = user.updateProfile("Ada", "OldAlias", 42, "es", "en");
    arrangeUser();
    when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var result = update.updateMyProfile(new UpdateMyProfileCommand("Ada", null, null, "es", "en"));

    assertThat(result.alias()).isNull();
    assertThat(result.age()).isNull();
  }

  @Test
  void rejectsCaseInsensitiveDuplicateAliasBeforeSaving() {
    arrangeUser();
    when(users.existsByAliasIgnoreCaseAndIdNot("MARLON", userId)).thenReturn(true);

    assertThatThrownBy(
            () ->
                update.updateMyProfile(
                    new UpdateMyProfileCommand("Ada", "MARLON", null, null, "en")))
        .isInstanceOf(AliasAlreadyInUseException.class);
    verify(users, never()).save(any());
  }

  @Test
  void validatesNameAgeAliasAndLearningLanguage() {
    arrangeUser();

    assertThatThrownBy(
            () -> update.updateMyProfile(new UpdateMyProfileCommand(" ", null, null, null, "en")))
        .isInstanceOf(InvalidApplicationArgumentException.class);
    assertThatThrownBy(
            () -> update.updateMyProfile(new UpdateMyProfileCommand("Ada", null, 4, null, "en")))
        .isInstanceOf(InvalidApplicationArgumentException.class);
    assertThatThrownBy(
            () -> update.updateMyProfile(new UpdateMyProfileCommand("Ada", " ", null, null, "en")))
        .isInstanceOf(InvalidApplicationArgumentException.class);
    assertThatThrownBy(
            () -> update.updateMyProfile(new UpdateMyProfileCommand("Ada", null, null, null, "fr")))
        .isInstanceOf(InvalidApplicationArgumentException.class);
  }

  private void arrangeUser() {
    when(currentUser.requireUserId()).thenReturn(userId);
    when(users.findById(userId)).thenReturn(Optional.of(user));
  }
}
