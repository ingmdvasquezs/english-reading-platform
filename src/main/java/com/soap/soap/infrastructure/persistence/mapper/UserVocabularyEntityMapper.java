package com.soap.soap.infrastructure.persistence.mapper;

import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.infrastructure.persistence.entity.UserVocabularyEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    uses = {UserEntityMapper.class, WordEntityMapper.class})
public interface UserVocabularyEntityMapper {

  UserVocabulary toDomain(UserVocabularyEntity entity);

  UserVocabularyEntity toEntity(UserVocabulary domain);
}
