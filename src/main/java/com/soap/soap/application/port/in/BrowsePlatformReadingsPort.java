package com.soap.soap.application.port.in;

import com.soap.soap.application.model.BrowsePlatformReadingsQuery;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.RecommendedPlatformReading;

public interface BrowsePlatformReadingsPort {
  PageResult<RecommendedPlatformReading> browsePlatformReadings(BrowsePlatformReadingsQuery query);
}
