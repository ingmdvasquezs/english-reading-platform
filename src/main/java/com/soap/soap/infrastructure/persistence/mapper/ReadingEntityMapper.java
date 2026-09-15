package com.soap.soap.infrastructure.persistence.mapper;

import com.soap.soap.domain.model.LanguageTag;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.infrastructure.persistence.entity.ReadingEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = UserEntityMapper.class)
public interface ReadingEntityMapper {

  Reading toDomain(ReadingEntity entity);

  ReadingEntity toEntity(Reading domain);

  default String map(LanguageTag tag) {
    return tag == null ? null : tag.value();
  }

  default LanguageTag map(String value) {
    return value == null ? null : LanguageTag.of(value);
  }
}
