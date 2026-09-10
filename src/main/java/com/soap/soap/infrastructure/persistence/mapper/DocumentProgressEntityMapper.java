package com.soap.soap.infrastructure.persistence.mapper;

import com.soap.soap.domain.model.DocumentProgress;
import com.soap.soap.infrastructure.persistence.entity.DocumentProgressEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface DocumentProgressEntityMapper {
  DocumentProgress toDomain(DocumentProgressEntity entity);

  DocumentProgressEntity toEntity(DocumentProgress domain);
}
