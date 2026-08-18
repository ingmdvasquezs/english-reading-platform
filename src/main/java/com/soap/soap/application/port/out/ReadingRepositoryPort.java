package com.soap.soap.application.port.out;

import com.soap.soap.domain.model.Reading;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReadingRepositoryPort {

  Optional<Reading> findById(UUID id);

  List<Reading> findByUserId(UUID userId);

  Reading save(Reading reading);
}
