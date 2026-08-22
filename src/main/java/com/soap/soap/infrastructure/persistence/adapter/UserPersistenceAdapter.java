package com.soap.soap.infrastructure.persistence.adapter;

import com.soap.soap.application.exception.EmailAlreadyRegisteredException;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.domain.model.User;
import com.soap.soap.infrastructure.persistence.mapper.UserEntityMapper;
import com.soap.soap.infrastructure.persistence.repository.JpaUserRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UserPersistenceAdapter implements UserRepositoryPort {

  private final JpaUserRepository repository;
  private final UserEntityMapper mapper;

  @Override
  @Transactional(readOnly = true)
  public Optional<User> findById(UUID id) {
    return repository.findById(id).map(mapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsById(UUID id) {
    return repository.existsById(id);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<User> findByEmail(String email) {
    return repository.findByEmailIgnoreCase(email).map(mapper::toDomain);
  }

  @Override
  @Transactional
  public User save(User user) {
    try {
      var entity = mapper.toEntity(user);
      return mapper.toDomain(repository.saveAndFlush(entity));
    } catch (DataIntegrityViolationException exception) {
      if (hasConstraint(exception, "uk_users_email_normalized")) {
        throw new EmailAlreadyRegisteredException();
      }
      throw exception;
    }
  }

  private boolean hasConstraint(Throwable exception, String constraint) {
    for (var cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException violation
          && constraint.equals(violation.getConstraintName())) {
        return true;
      }
      if (cause.getMessage() != null && cause.getMessage().contains(constraint)) {
        return true;
      }
    }
    return false;
  }
}
