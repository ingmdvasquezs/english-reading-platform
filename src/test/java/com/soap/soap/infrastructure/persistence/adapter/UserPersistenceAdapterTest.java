package com.soap.soap.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.EmailAlreadyRegisteredException;
import com.soap.soap.domain.model.User;
import com.soap.soap.infrastructure.persistence.entity.UserEntity;
import com.soap.soap.infrastructure.persistence.mapper.UserEntityMapper;
import com.soap.soap.infrastructure.persistence.repository.JpaUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class UserPersistenceAdapterTest {
  @Test
  void translatesAConcurrentEmailConstraintViolation() {
    var repository = org.mockito.Mockito.mock(JpaUserRepository.class);
    var mapper = org.mockito.Mockito.mock(UserEntityMapper.class);
    var user = new User(null, "Ada", "ada@example.com", "hash", null);
    var entity = new UserEntity();
    when(mapper.toEntity(user)).thenReturn(entity);
    when(repository.saveAndFlush(entity))
        .thenThrow(new DataIntegrityViolationException("uk_users_email_normalized"));

    assertThatThrownBy(() -> new UserPersistenceAdapter(repository, mapper).save(user))
        .isInstanceOf(EmailAlreadyRegisteredException.class)
        .hasMessage("Email is already registered");
  }

  @Test
  void preservesUnknownIntegrityViolations() {
    var repository = org.mockito.Mockito.mock(JpaUserRepository.class);
    var mapper = org.mockito.Mockito.mock(UserEntityMapper.class);
    var user = new User(null, "Ada", "ada@example.com", "hash", null);
    var entity = new UserEntity();
    var failure = new DataIntegrityViolationException("unrelated_constraint");
    when(mapper.toEntity(user)).thenReturn(entity);
    when(repository.saveAndFlush(entity)).thenThrow(failure);

    assertThatThrownBy(() -> new UserPersistenceAdapter(repository, mapper).save(user))
        .isSameAs(failure);
  }
}
