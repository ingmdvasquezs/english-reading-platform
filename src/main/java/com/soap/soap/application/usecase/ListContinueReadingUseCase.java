package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.UserNotFoundException;
import com.soap.soap.application.model.ContinueReadingItem;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.port.in.ListContinueReadingPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ListContinueReadingUseCase implements ListContinueReadingPort {
  private final UserRepositoryPort users;
  private final ReadingProgressRepositoryPort progress;
  private final CurrentUserPort currentUser;

  @Override
  @Transactional(readOnly = true)
  public PageResult<ContinueReadingItem> listContinueReading(PageRequest pageRequest) {
    var userId = currentUser.requireUserId();
    if (pageRequest == null) {
      throw new InvalidApplicationArgumentException("Page request must not be null");
    }
    if (!users.existsById(userId)) {
      throw new UserNotFoundException(userId);
    }
    return progress.findInProgressReadings(userId, pageRequest);
  }
}
