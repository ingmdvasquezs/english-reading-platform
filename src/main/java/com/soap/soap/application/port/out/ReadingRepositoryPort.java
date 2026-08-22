package com.soap.soap.application.port.out;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.ReadingSummary;
import com.soap.soap.domain.model.Reading;
import java.util.Optional;
import java.util.UUID;

public interface ReadingRepositoryPort {

  Optional<Reading> findById(UUID id);

  PageResult<ReadingSummary> findSummariesByUserId(UUID userId, PageRequest pageRequest);

  Reading save(Reading reading);
}
