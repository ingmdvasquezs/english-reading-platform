package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.UserNotFoundException;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.port.in.ListUserReadingsPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.domain.model.Reading;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ListUserReadingsUseCase implements ListUserReadingsPort {
  private final UserRepositoryPort users;
  private final ReadingRepositoryPort readings;

  @Override
  @Transactional(readOnly = true)
  public PageResult<Reading> listUserReadings(UUID userId, PageRequest pageRequest) {
    if (userId == null) {
      throw new InvalidApplicationArgumentException("User id must not be null");
    }
    if (pageRequest == null) {
      throw new InvalidApplicationArgumentException("Page request must not be null");
    }
    if (!users.existsById(userId)) {
      throw new UserNotFoundException(userId);
    }
    return readings.findByUserId(userId, pageRequest);
  }
}
