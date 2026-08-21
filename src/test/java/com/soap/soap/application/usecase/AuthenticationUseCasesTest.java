package com.soap.soap.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.soap.soap.application.command.LoginCommand;
import com.soap.soap.application.command.RegisterUserCommand;
import com.soap.soap.application.exception.EmailAlreadyRegisteredException;
import com.soap.soap.application.exception.InvalidCredentialsException;
import com.soap.soap.application.model.AccessToken;
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
        new RegisterUserUseCase(users, passwords)
            .registerUser(new RegisterUserCommand(" Ada ", " ADA@Example.COM ", "secret123"));
    var saved = ArgumentCaptor.forClass(User.class);
    verify(users).save(saved.capture());
    assertThat(saved.getValue().email()).isEqualTo("ada@example.com");
    assertThat(saved.getValue().passwordHash()).isEqualTo("bcrypt-hash");
    assertThat(result.name()).isEqualTo("Ada");
  }

  @Test
  void rejectsADuplicateEmailWithoutHashing() {
    when(users.findByEmail("ada@example.com"))
        .thenReturn(Optional.of(new User(UUID.randomUUID(), "Ada", "ada@example.com")));
    assertThatThrownBy(
            () ->
                new RegisterUserUseCase(users, passwords)
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
    assertThat(
            new LoginUseCase(users, passwords, tokens)
                .login(new LoginCommand("ADA@example.com", "secret123")))
        .isEqualTo(token);
  }

  @Test
  void rejectsInvalidCredentialsWithoutGeneratingAToken() {
    when(users.findByEmail("ada@example.com")).thenReturn(Optional.empty());
    assertThatThrownBy(
            () ->
                new LoginUseCase(users, passwords, tokens)
                    .login(new LoginCommand("ada@example.com", "wrong")))
        .isInstanceOf(InvalidCredentialsException.class);
    verifyNoInteractions(tokens);
  }
}
