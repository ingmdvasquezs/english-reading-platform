package com.soap.soap.infrastructure.persistence.mapper;

import com.soap.soap.domain.model.OutboxEvent;
import com.soap.soap.infrastructure.persistence.entity.OutboxEventEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface OutboxEventEntityMapper {
  OutboxEvent toDomain(OutboxEventEntity entity);

  OutboxEventEntity toEntity(OutboxEvent domain);
}
