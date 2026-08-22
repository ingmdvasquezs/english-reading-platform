package com.soap.soap.application.usecase;

import com.soap.soap.application.command.LoginCommand;
import com.soap.soap.application.exception.InvalidCredentialsException;
import com.soap.soap.application.model.AccessToken;
import com.soap.soap.application.port.in.LoginPort;
import com.soap.soap.application.port.out.PasswordEncoderPort;
import com.soap.soap.application.port.out.TokenProviderPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginUseCase implements LoginPort {
  private final UserRepositoryPort users;
  private final PasswordEncoderPort passwords;
  private final TokenProviderPort tokens;

  @Override
  public AccessToken login(LoginCommand command) {
    if (command == null || command.email() == null || command.password() == null)
      throw new InvalidCredentialsException();
    var user =
        users
            .findByEmail(command.email().trim().toLowerCase(Locale.ROOT))
            .orElseThrow(InvalidCredentialsException::new);
    if (!passwords.matches(command.password(), user.passwordHash()))
      throw new InvalidCredentialsException();
    return tokens.create(user);
  }
}
