package com.soap.soap.infrastructure.persistence.mapper;

import com.soap.soap.domain.model.User;
import com.soap.soap.infrastructure.persistence.entity.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserEntityMapper {

  User toDomain(UserEntity entity);

  UserEntity toEntity(User domain);
}
