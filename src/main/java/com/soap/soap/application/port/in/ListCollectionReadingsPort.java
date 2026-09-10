package com.soap.soap.application.port.in;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.RecommendedPlatformReading;

public interface ListCollectionReadingsPort {
  PageResult<RecommendedPlatformReading> listCollectionReadings(
      String collectionKey, PageRequest pageRequest);
}
