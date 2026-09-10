package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.UserNotFoundException;
import com.soap.soap.application.model.UserProfile;
import com.soap.soap.application.port.in.GetMyProfilePort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.domain.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetMyProfileUseCase implements GetMyProfilePort {
  private final CurrentUserPort currentUser;
  private final UserRepositoryPort users;

  @Override
  @Transactional(readOnly = true)
  public UserProfile getMyProfile() {
    var userId = currentUser.requireUserId();
    return toProfile(users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId)));
  }

  static UserProfile toProfile(User user) {
    return new UserProfile(
        user.name(),
        user.alias(),
        user.age(),
        user.nativeLanguage(),
        user.learningLanguage(),
        user.email());
  }
}
