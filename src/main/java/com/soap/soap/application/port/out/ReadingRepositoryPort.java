package com.soap.soap.application.port.out;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.domain.model.Reading;
import java.util.Optional;
import java.util.UUID;

public interface ReadingRepositoryPort {

  Optional<Reading> findById(UUID id);

  PageResult<Reading> findByUserId(UUID userId, PageRequest pageRequest);

  Reading save(Reading reading);
}
