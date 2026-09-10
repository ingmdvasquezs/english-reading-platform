package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.UserNotFoundException;
import com.soap.soap.application.port.in.ListCollectionsPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingCollectionRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.domain.model.ReadingCollection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ListCollectionsUseCase implements ListCollectionsPort {
  private final UserRepositoryPort users;
  private final ReadingCollectionRepositoryPort collections;
  private final CurrentUserPort currentUser;

  @Override
  @Transactional(readOnly = true)
  public List<ReadingCollection> listCollections() {
    var userId = currentUser.requireUserId();
    if (!users.existsById(userId)) {
      throw new UserNotFoundException(userId);
    }
    return collections.findAllActive();
  }
}
