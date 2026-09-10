package com.soap.soap.infrastructure.persistence.mapper;

import com.soap.soap.domain.model.ImportedDocument;
import com.soap.soap.infrastructure.persistence.entity.ImportedDocumentEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ImportedDocumentEntityMapper {
  ImportedDocument toDomain(ImportedDocumentEntity entity);

  @Mapping(target = "deduplicationSha256", ignore = true)
  ImportedDocumentEntity toEntity(ImportedDocument domain);
}
