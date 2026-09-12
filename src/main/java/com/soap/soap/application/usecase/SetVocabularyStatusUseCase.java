package com.soap.soap.application.usecase;

import com.soap.soap.application.command.SetVocabularyStatusCommand;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.UserNotFoundException;
import com.soap.soap.application.model.InputLimits;
import com.soap.soap.application.port.in.SetVocabularyStatusPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.service.LanguageNormalizer;
import com.soap.soap.application.service.TextWordProcessor;
import com.soap.soap.application.service.WordResolver;
import com.soap.soap.domain.model.UserVocabulary;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SetVocabularyStatusUseCase implements SetVocabularyStatusPort {
  private final UserRepositoryPort users;
  private final UserVocabularyRepositoryPort vocabulary;
  private final TextWordProcessor wordProcessor;
  private final LanguageNormalizer languages;
  private final WordResolver wordResolver;
  private final Clock clock;
  private final CurrentUserPort currentUser;
  private final InputLimits inputLimits;

  @Override
  @Transactional
  public UserVocabulary setVocabularyStatus(SetVocabularyStatusCommand command) {
    if (command == null) {
      throw new InvalidApplicationArgumentException("Command must not be null");
    }
    if (command.word().length() > inputLimits.maxWordCharacters()) {
      throw new InvalidApplicationArgumentException("Word is too long");
    }
    var userId = currentUser.requireUserId();
    var user = users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    var language = languages.normalize(command.language());
    var normalizedValue = wordProcessor.normalize(command.word());
    var word = wordResolver.resolve(normalizedValue, language);
    var existing = vocabulary.findByUserIdAndWordId(userId, word.id());
    if (existing.isPresent()) {
      return vocabulary.save(existing.orElseThrow().changeStatus(command.status(), clock));
    }
    return vocabulary.save(UserVocabulary.createInitial(user, word, command.status(), clock));
  }
}
