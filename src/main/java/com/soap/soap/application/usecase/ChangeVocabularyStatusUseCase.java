package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.UserNotFoundException;
import com.soap.soap.application.exception.VocabularyEntryNotFoundException;
import com.soap.soap.application.port.in.ChangeVocabularyStatusPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.model.VocabularyStatus;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ChangeVocabularyStatusUseCase implements ChangeVocabularyStatusPort {
  private final UserRepositoryPort users;
  private final UserVocabularyRepositoryPort vocabulary;
  private final Clock clock;

  @Override
  @Transactional
  public UserVocabulary changeVocabularyStatus(UUID userId, UUID wordId, VocabularyStatus status) {
    if (userId == null) {
      throw new InvalidApplicationArgumentException("User id must not be null");
    }
    if (wordId == null) {
      throw new InvalidApplicationArgumentException("Word id must not be null");
    }
    if (status == null) {
      throw new InvalidApplicationArgumentException("Vocabulary status must not be null");
    }
    if (!users.existsById(userId)) {
      throw new UserNotFoundException(userId);
    }
    var current =
        vocabulary
            .findByUserIdAndWordId(userId, wordId)
            .orElseThrow(() -> new VocabularyEntryNotFoundException(userId, wordId));
    return vocabulary.save(current.changeStatus(status, clock));
  }
}
