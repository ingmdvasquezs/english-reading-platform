package com.soap.soap.infrastructure.persistence.mapper;

import com.soap.soap.domain.model.DocumentUnit;
import com.soap.soap.infrastructure.persistence.entity.DocumentUnitEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface DocumentUnitEntityMapper {
  DocumentUnit toDomain(DocumentUnitEntity entity);

  @Mapping(target = "new", ignore = true)
  DocumentUnitEntity toEntity(DocumentUnit domain);
}
