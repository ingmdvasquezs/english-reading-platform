package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.soap.soap.application.command.RegisterReadingCommand;
import com.soap.soap.application.command.RegisterUserCommand;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.model.InputLimits;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.DictionaryPort;
import com.soap.soap.application.port.out.PasswordEncoderPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.TranslationPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.service.LanguageNormalizer;
import com.soap.soap.application.service.TextWordProcessor;
import com.soap.soap.domain.model.User;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InputLimitsUseCasesTest {
  private final InputLimits limits = InputLimits.defaults();

  @Test
  void rejectsOversizedUserFields() {
    var users = mock(UserRepositoryPort.class);
    var useCase = new RegisterUserUseCase(users, mock(PasswordEncoderPort.class), limits);

    assertInvalid(
        () ->
            useCase.registerUser(new RegisterUserCommand("n".repeat(101), "a@b.co", "secret123")));
    assertInvalid(
        () ->
            useCase.registerUser(
                new RegisterUserCommand("Ada", "a".repeat(245) + "@example.com", "secret123")));
    assertInvalid(
        () -> useCase.registerUser(new RegisterUserCommand("Ada", "a@b.co", "p".repeat(129))));
  }

  @Test
  void rejectsOversizedReadingTitleAndUtf8Content() {
    var users = mock(UserRepositoryPort.class);
    var currentUser = mock(CurrentUserPort.class);
    var userId = UUID.randomUUID();
    when(currentUser.requireUserId()).thenReturn(userId);
    when(users.findById(userId))
        .thenReturn(Optional.of(new User(userId, "Ada", "ada@example.com")));
    var useCase =
        new RegisterReadingUseCase(
            users,
            mock(ReadingRepositoryPort.class),
            new LanguageNormalizer(),
            currentUser,
            limits);

    assertInvalid(
        () -> useCase.registerReading(new RegisterReadingCommand("t".repeat(201), "x", "en")));
    assertInvalid(
        () ->
            useCase.registerReading(
                new RegisterReadingCommand("Title", "á".repeat(524_289), "en")));
  }

  @Test
  void rejectsOversizedLookupWordBeforeCallingProviders() {
    var currentUser = mock(CurrentUserPort.class);
    when(currentUser.requireUserId()).thenReturn(UUID.randomUUID());
    var useCase =
        new LookupWordUseCase(
            mock(DictionaryPort.class),
            mock(TranslationPort.class),
            new TextWordProcessor(),
            currentUser,
            limits);

    assertInvalid(() -> useCase.lookupWord("w".repeat(101)));
  }

  private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
    assertThatThrownBy(callable).isInstanceOf(InvalidApplicationArgumentException.class);
  }
}
