package com.soap.soap.application.port.in;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.domain.model.Reading;
import java.util.UUID;

public interface ListUserReadingsPort {
  PageResult<Reading> listUserReadings(UUID userId, PageRequest pageRequest);
}
