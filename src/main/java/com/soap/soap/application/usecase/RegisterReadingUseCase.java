package com.soap.soap.application.usecase;

import com.soap.soap.application.command.RegisterReadingCommand;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.UserNotFoundException;
import com.soap.soap.application.model.InputLimits;
import com.soap.soap.application.port.in.RegisterReadingPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.service.LanguageNormalizer;
import com.soap.soap.domain.model.Reading;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RegisterReadingUseCase implements RegisterReadingPort {

  private final UserRepositoryPort users;
  private final ReadingRepositoryPort readings;
  private final LanguageNormalizer languages;
  private final CurrentUserPort currentUser;
  private final InputLimits limits;

  @Override
  @Transactional
  public Reading registerReading(RegisterReadingCommand command) {
    if (command == null) {
      throw new InvalidApplicationArgumentException("Command must not be null");
    }
    var userId = currentUser.requireUserId();
    if (command.title().length() > limits.maxTitleCharacters()) {
      throw new InvalidApplicationArgumentException(
          "Title must contain at most " + limits.maxTitleCharacters() + " characters");
    }
    if (command.content().getBytes(StandardCharsets.UTF_8).length
        > limits.maxReadingContentBytes()) {
      throw new InvalidApplicationArgumentException(
          "Reading content exceeds the maximum UTF-8 size");
    }
    var user = users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));

    return readings.save(
        new Reading(
            null,
            user,
            command.title(),
            command.content(),
            languages.normalize(command.language()),
            null));
  }
}
