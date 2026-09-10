package com.soap.soap.application.port.in;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.PlatformReadingSummary;

public interface ListPlatformReadingsPort {
  PageResult<PlatformReadingSummary> listPlatformReadings(PageRequest pageRequest);
}
