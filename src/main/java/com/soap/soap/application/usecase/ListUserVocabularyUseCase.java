package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.UserNotFoundException;
import com.soap.soap.application.model.UserVocabularyPage;
import com.soap.soap.application.model.VocabularyQuery;
import com.soap.soap.application.port.in.ListUserVocabularyPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.application.service.TextWordProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ListUserVocabularyUseCase implements ListUserVocabularyPort {
  private final UserRepositoryPort users;
  private final UserVocabularyRepositoryPort vocabulary;
  private final CurrentUserPort currentUser;
  private final TextWordProcessor wordProcessor;

  @Override
  @Transactional(readOnly = true)
  public UserVocabularyPage listUserVocabulary(VocabularyQuery query) {
    if (query == null) {
      throw new InvalidApplicationArgumentException("Vocabulary query must not be null");
    }
    var userId = currentUser.requireUserId();
    if (!users.existsById(userId)) {
      throw new UserNotFoundException(userId);
    }

    String normalizedPrefix = null;
    if (query.search() != null && !query.search().isBlank()) {
      normalizedPrefix = wordProcessor.normalize(query.search().trim());
    }

    var page =
        vocabulary.findByUserIdAndCriteria(
            userId, query.status(), normalizedPrefix, query.pageRequest());
    var summary = vocabulary.countSummaryByUserId(userId);

    return new UserVocabularyPage(page, summary);
  }
}
