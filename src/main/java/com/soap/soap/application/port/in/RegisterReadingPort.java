package com.soap.soap.application.port.in;

import com.soap.soap.application.command.RegisterReadingCommand;
import com.soap.soap.domain.model.Reading;

public interface RegisterReadingPort {
  Reading registerReading(RegisterReadingCommand command);
}
