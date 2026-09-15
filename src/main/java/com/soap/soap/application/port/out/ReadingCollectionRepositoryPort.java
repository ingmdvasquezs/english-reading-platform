package com.soap.soap.application.port.out;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingCollection;
import java.util.List;
import java.util.Optional;

public interface ReadingCollectionRepositoryPort {
  List<ReadingCollection> findAllActive();

  Optional<ReadingCollection> findActiveByKey(String key);

  PageResult<Reading> findReadings(String key, PageRequest pageRequest);

  PageResult<Reading> findReadings(String key, String language, PageRequest pageRequest);
}
