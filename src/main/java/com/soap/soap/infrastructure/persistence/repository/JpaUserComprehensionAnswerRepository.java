package com.soap.soap.infrastructure.persistence.repository;

import com.soap.soap.infrastructure.persistence.entity.UserComprehensionAnswerEntity;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaUserComprehensionAnswerRepository
    extends JpaRepository<UserComprehensionAnswerEntity, UUID> {

  @Query(
      "SELECT COUNT(a) > 0 FROM UserComprehensionAnswerEntity a WHERE a.question.id = :questionId")
  boolean existsByQuestionId(@Param("questionId") UUID questionId);

  @Query(
      "SELECT COUNT(a) > 0 FROM UserComprehensionAnswerEntity a WHERE a.selectedOption.id = :optionId")
  boolean existsBySelectedOptionId(@Param("optionId") UUID optionId);

  @Query(
      "SELECT COUNT(a) > 0 FROM UserComprehensionAnswerEntity a WHERE a.question.id IN :questionIds")
  boolean existsByQuestionIdIn(@Param("questionIds") Collection<UUID> questionIds);

  @Query(
      "SELECT COUNT(a) > 0 FROM UserComprehensionAnswerEntity a WHERE a.selectedOption.id IN :optionIds")
  boolean existsBySelectedOptionIdIn(@Param("optionIds") Collection<UUID> optionIds);
}
