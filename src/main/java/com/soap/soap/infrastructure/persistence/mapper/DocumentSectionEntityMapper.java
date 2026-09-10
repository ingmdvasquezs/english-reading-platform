package com.soap.soap.infrastructure.persistence.mapper;

import com.soap.soap.domain.model.DocumentSection;
import com.soap.soap.infrastructure.persistence.entity.DocumentSectionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface DocumentSectionEntityMapper {
  DocumentSection toDomain(DocumentSectionEntity entity);

  DocumentSectionEntity toEntity(DocumentSection domain);
}
