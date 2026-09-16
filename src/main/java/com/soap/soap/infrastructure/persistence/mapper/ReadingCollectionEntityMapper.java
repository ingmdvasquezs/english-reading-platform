package com.soap.soap.infrastructure.persistence.mapper;

import com.soap.soap.domain.model.ReadingCollection;
import com.soap.soap.infrastructure.persistence.entity.ReadingCollectionEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReadingCollectionEntityMapper {
  ReadingCollection toDomain(ReadingCollectionEntity entity);

  ReadingCollectionEntity toEntity(ReadingCollection domain);
}
