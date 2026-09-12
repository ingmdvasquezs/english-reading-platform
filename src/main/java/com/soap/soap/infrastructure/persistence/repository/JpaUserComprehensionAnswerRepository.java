package com.soap.soap.infrastructure.persistence.repository;

import com.soap.soap.infrastructure.persistence.entity.UserComprehensionAnswerEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaUserComprehensionAnswerRepository
    extends JpaRepository<UserComprehensionAnswerEntity, UUID> {}
