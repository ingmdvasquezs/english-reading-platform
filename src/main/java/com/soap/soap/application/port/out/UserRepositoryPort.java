package com.soap.soap.application.port.out;

import com.soap.soap.domain.model.User;
import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {

  Optional<User> findById(UUID id);

  boolean existsById(UUID id);

  Optional<User> findByEmail(String email);

  User save(User user);
}
