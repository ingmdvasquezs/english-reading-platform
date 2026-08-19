package com.soap.soap.infrastructure.persistence.mapper;

import com.soap.soap.domain.model.Word;
import com.soap.soap.infrastructure.persistence.entity.WordEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface WordEntityMapper {

  Word toDomain(WordEntity entity);

  @Mapping(target = "createdAt", ignore = true)
  WordEntity toEntity(Word domain);
}
