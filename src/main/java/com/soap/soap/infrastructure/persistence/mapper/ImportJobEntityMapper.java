package com.soap.soap.infrastructure.persistence.mapper;

import com.soap.soap.domain.model.ImportJob;
import com.soap.soap.infrastructure.persistence.entity.ImportJobEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ImportJobEntityMapper {
  ImportJob toDomain(ImportJobEntity entity);

  ImportJobEntity toEntity(ImportJob domain);
}
