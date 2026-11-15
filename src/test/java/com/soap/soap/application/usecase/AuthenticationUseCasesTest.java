package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.soap.soap.application.command.LoginCommand;
import com.soap.soap.application.command.RegisterUserCommand;
import com.soap.soap.application.exception.EmailAlreadyRegisteredException;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.InvalidCredentialsException;
import com.soap.soap.application.model.AccessToken;
import com.soap.soap.application.model.InputLimits;
import com.soap.soap.application.model.LoginResult;
import com.soap.soap.application.port.out.PasswordEncoderPort;
import com.soap.soap.application.port.out.TokenProviderPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.domain.model.User;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthenticationUseCasesTest {
  @Mock private UserRepositoryPort users;
  @Mock private PasswordEncoderPort passwords;
  @Mock private TokenProviderPort tokens;

  @Test
  void registersWithNormalizedEmailAndOnlyThePasswordHash() {
    when(passwords.encode("secret123")).thenReturn("bcrypt-hash");
    when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    var result =
        new RegisterUserUseCase(users, passwords, InputLimits.defaults())
            .registerUser(new RegisterUserCommand(" Ada ", " ADA@Example.COM ", "secret123"));
    var saved = ArgumentCaptor.forClass(User.class);
    verify(users).save(saved.capture());
    assertThat(saved.getValue().email()).isEqualTo("ada@example.com");
    assertThat(saved.getValue().passwordHash()).isEqualTo("bcrypt-hash");
    assertThat(result.name()).isEqualTo("Ada");
    assertThat(result.onboardingCompleted()).isFalse();
  }

  @Test
  void rejectsADuplicateEmailWithoutHashing() {
    when(users.findByEmail("ada@example.com"))
        .thenReturn(Optional.of(new User(UUID.randomUUID(), "Ada", "ada@example.com")));
    assertThatThrownBy(
            () ->
                new RegisterUserUseCase(users, passwords, InputLimits.defaults())
                    .registerUser(new RegisterUserCommand("Ada", "ADA@example.com", "secret123")))
        .isInstanceOf(EmailAlreadyRegisteredException.class);
    verify(passwords, never()).encode(any());
  }

  @Test
  void logsInAndGeneratesAToken() {
    var user = new User(UUID.randomUUID(), "Ada", "ada@example.com", "hash", LocalDateTime.now());
    var token = new AccessToken("jwt", "Bearer", 3600);
    when(users.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
    when(passwords.matches("secret123", "hash")).thenReturn(true);
    when(tokens.create(user)).thenReturn(token);
    var result =
        new LoginUseCase(users, passwords, tokens, InputLimits.defaults())
            .login(new LoginCommand("ADA@example.com", "secret123"));
    assertThat(result).isEqualTo(new LoginResult(token, false));
  }

  @Test
  void rejectsInvalidCredentialsWithoutGeneratingAToken() {
    when(users.findByEmail("ada@example.com")).thenReturn(Optional.empty());
    assertThatThrownBy(
            () ->
                new LoginUseCase(users, passwords, tokens, InputLimits.defaults())
                    .login(new LoginCommand("ada@example.com", "wrong")))
        .isInstanceOf(InvalidCredentialsException.class);
    verifyNoInteractions(tokens);
  }

  @Test
  void rejectsOversizedEmailBeforeRepositoryOrBcryptWork() {
    assertThatThrownBy(
            () ->
                new LoginUseCase(users, passwords, tokens, InputLimits.defaults())
                    .login(new LoginCommand("a".repeat(255), "secret123")))
        .isInstanceOf(InvalidCredentialsException.class);

    verifyNoInteractions(users, passwords, tokens);
  }

  @Test
  void rejectsOversizedPasswordBeforeRepositoryOrBcryptWork() {
    assertThatThrownBy(
            () ->
                new LoginUseCase(users, passwords, tokens, InputLimits.defaults())
                    .login(new LoginCommand("ada@example.com", "x".repeat(129))))
        .isInstanceOf(InvalidCredentialsException.class);

    verifyNoInteractions(users, passwords, tokens);
  }

  @Test
  void registerAcceptsPasswordsAtTheBcryptByteLimit() {
    when(passwords.encode(any())).thenReturn("bcrypt-hash");
    when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    var useCase = new RegisterUserUseCase(users, passwords, InputLimits.defaults());

    useCase.registerUser(new RegisterUserCommand("Ada", "ascii@example.com", "x".repeat(72)));
    useCase.registerUser(new RegisterUserCommand("Ada", "utf8@example.com", "é".repeat(36)));

    verify(passwords).encode("x".repeat(72));
    verify(passwords).encode("é".repeat(36));
  }

  @Test
  void registerRejectsPasswordsAboveTheBcryptByteLimitBeforeHashing() {
    var useCase = new RegisterUserUseCase(users, passwords, InputLimits.defaults());

    for (var password : new String[] {"x".repeat(73), "é".repeat(37), "€".repeat(25)}) {
      assertThatThrownBy(
              () ->
                  useCase.registerUser(new RegisterUserCommand("Ada", "ada@example.com", password)))
          .isInstanceOf(InvalidApplicationArgumentException.class)
          .hasMessageContaining("72 bytes");
    }
    verifyNoInteractions(users, passwords);
  }

  @Test
  void loginRejectsPasswordsAboveTheBcryptByteLimitBeforeRepositoryOrBcryptWork() {
    var useCase = new LoginUseCase(users, passwords, tokens, InputLimits.defaults());

    for (var password : new String[] {"x".repeat(73), "é".repeat(37), "€".repeat(25)}) {
      assertThatThrownBy(() -> useCase.login(new LoginCommand("ada@example.com", password)))
          .isInstanceOf(InvalidCredentialsException.class);
    }
    verifyNoInteractions(users, passwords, tokens);
  }

  @Test
  void acceptsCredentialLengthsAtTheirBoundaries() {
    var email = "a".repeat(242) + "@example.com";
    // 72 UTF-8 bytes is the effective BCrypt boundary (below the 128-character input limit).
    var password = "x".repeat(72);
    var user = new User(UUID.randomUUID(), "Ada", email, "hash", LocalDateTime.now(), true);
    var token = new AccessToken("jwt", "Bearer", 3600);
    when(users.findByEmail(email)).thenReturn(Optional.of(user));
    when(passwords.matches(password, "hash")).thenReturn(true);
    when(tokens.create(user)).thenReturn(token);

    assertThat(
            new LoginUseCase(users, passwords, tokens, InputLimits.defaults())
                .login(new LoginCommand(email, password)))
        .isEqualTo(new LoginResult(token, true));
  }
}
