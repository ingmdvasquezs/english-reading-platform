package com.soap.soap.application.usecase;

import com.soap.soap.application.command.AddWordToVocabularyCommand;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.UserNotFoundException;
import com.soap.soap.application.exception.WordAlreadyInVocabularyException;
import com.soap.soap.application.port.in.AddWordToVocabularyPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.service.LanguageNormalizer;
import com.soap.soap.application.service.TextWordProcessor;
import com.soap.soap.application.service.WordResolver;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.model.VocabularyStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AddWordToVocabularyUseCase implements AddWordToVocabularyPort {
  private final UserRepositoryPort users;
  private final UserVocabularyRepositoryPort vocabulary;
  private final TextWordProcessor wordProcessor;
  private final LanguageNormalizer languages;
  private final WordResolver wordResolver;
  private final Clock clock;
  private final CurrentUserPort currentUser;

  @Override
  @Transactional
  public UserVocabulary addWordToVocabulary(AddWordToVocabularyCommand command) {
    if (command == null) {
      throw new InvalidApplicationArgumentException("Command must not be null");
    }
    var userId = currentUser.requireUserId();
    var status = command.initialStatus();
    var language = languages.normalize(command.language());
    var normalizedValue = wordProcessor.normalize(command.word());
    var user = users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    var word = wordResolver.resolve(normalizedValue, language);

    if (vocabulary.findByUserIdAndWordId(userId, word.id()).isPresent()) {
      throw new WordAlreadyInVocabularyException(normalizedValue);
    }

    var now = LocalDateTime.now(clock);
    var learnedAt = status == VocabularyStatus.KNOWN ? now : null;
    return vocabulary.save(new UserVocabulary(null, user, word, status, now, learnedAt));
  }
}
