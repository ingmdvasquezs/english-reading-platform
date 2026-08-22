package com.soap.soap.application.port.in;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.ReadingSummary;

public interface ListUserReadingsPort {
  PageResult<ReadingSummary> listUserReadings(PageRequest pageRequest);
}
