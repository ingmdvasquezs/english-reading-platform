package com.soap.soap.application.usecase;

import com.soap.soap.application.command.RegisterUserCommand;
import com.soap.soap.application.exception.EmailAlreadyRegisteredException;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.port.in.RegisterUserPort;
import com.soap.soap.application.port.out.PasswordEncoderPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.domain.model.User;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RegisterUserUseCase implements RegisterUserPort {
  private final UserRepositoryPort users;
  private final PasswordEncoderPort passwords;

  @Override
  @Transactional
  public User registerUser(RegisterUserCommand command) {
    if (command == null) throw new InvalidApplicationArgumentException("Command must not be null");
    var name = requireText(command.name(), "Name");
    var email = requireText(command.email(), "Email").toLowerCase(Locale.ROOT);
    if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
      throw new InvalidApplicationArgumentException("Email is invalid");
    if (command.password() == null || command.password().length() < 8)
      throw new InvalidApplicationArgumentException("Password must contain at least 8 characters");
    if (users.findByEmail(email).isPresent()) throw new EmailAlreadyRegisteredException();
    return users.save(new User(null, name, email, passwords.encode(command.password()), null));
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank())
      throw new InvalidApplicationArgumentException(field + " must not be blank");
    return value.trim();
  }
}
