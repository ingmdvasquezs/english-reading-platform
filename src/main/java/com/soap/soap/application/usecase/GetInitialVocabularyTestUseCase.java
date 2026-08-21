package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.UserNotFoundException;
import com.soap.soap.application.model.InitialVocabularyTest;
import com.soap.soap.application.port.in.GetInitialVocabularyTestPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.InitialVocabularyTestSourcePort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GetInitialVocabularyTestUseCase implements GetInitialVocabularyTestPort {
  private final UserRepositoryPort users;
  private final InitialVocabularyTestSourcePort source;
  private final CurrentUserPort currentUser;

  @Override
  public InitialVocabularyTest getInitialVocabularyTest() {
    var userId = currentUser.requireUserId();
    if (!users.existsById(userId)) {
      throw new UserNotFoundException(userId);
    }
    return source.load();
  }
}
