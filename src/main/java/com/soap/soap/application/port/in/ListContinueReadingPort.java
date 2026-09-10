package com.soap.soap.application.port.in;

import com.soap.soap.application.model.ContinueReadingItem;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;

public interface ListContinueReadingPort {
  PageResult<ContinueReadingItem> listContinueReading(PageRequest pageRequest);
}
