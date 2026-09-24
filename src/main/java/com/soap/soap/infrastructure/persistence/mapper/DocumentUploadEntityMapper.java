package com.soap.soap.infrastructure.persistence.mapper;

import com.soap.soap.domain.model.DocumentUpload;
import com.soap.soap.infrastructure.persistence.entity.DocumentUploadEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface DocumentUploadEntityMapper {
  DocumentUpload toDomain(DocumentUploadEntity entity);

  DocumentUploadEntity toEntity(DocumentUpload domain);
}
