package com.soap.soap.infrastructure.persistence.mapper;

import com.soap.soap.domain.model.OnboardingReading;
import com.soap.soap.infrastructure.persistence.entity.OnboardingReadingEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface OnboardingReadingEntityMapper {

  OnboardingReading toDomain(OnboardingReadingEntity entity);
}
