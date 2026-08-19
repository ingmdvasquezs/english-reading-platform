package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.UserNotFoundException;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.port.in.ListUserVocabularyPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.port.out.UserVocabularyRepositoryPort;
import com.soap.soap.domain.model.UserVocabulary;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ListUserVocabularyUseCase implements ListUserVocabularyPort {
  private final UserRepositoryPort users;
  private final UserVocabularyRepositoryPort vocabulary;

  @Override
  @Transactional(readOnly = true)
  public PageResult<UserVocabulary> listUserVocabulary(UUID userId, PageRequest pageRequest) {
    if (userId == null) {
      throw new InvalidApplicationArgumentException("User id must not be null");
    }
    if (pageRequest == null) {
      throw new InvalidApplicationArgumentException("Page request must not be null");
    }
    if (!users.existsById(userId)) {
      throw new UserNotFoundException(userId);
    }
    return vocabulary.findByUserId(userId, pageRequest);
  }
}
