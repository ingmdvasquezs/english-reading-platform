package com.soap.soap.infrastructure.persistence.repository;

import com.soap.soap.infrastructure.persistence.entity.ComprehensionOptionEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaComprehensionOptionRepository
    extends JpaRepository<ComprehensionOptionEntity, UUID> {}
