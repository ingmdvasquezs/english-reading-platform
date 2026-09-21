package com.soap.soap.infrastructure.persistence.repository;

import com.soap.soap.infrastructure.persistence.entity.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaUserRepository extends JpaRepository<UserEntity, UUID> {

  Optional<UserEntity> findByEmailIgnoreCase(String email);

  boolean existsByAliasIgnoreCaseAndIdNot(String alias, UUID id);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      update UserEntity u
      set u.onboardingCompleted = true
      where u.id = :id and u.onboardingCompleted = false
      """)
  int markOnboardingCompleted(@Param("id") UUID id);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from UserEntity u where u.id = :id")
  Optional<UserEntity> findByIdForUpdate(@Param("id") UUID id);
}
