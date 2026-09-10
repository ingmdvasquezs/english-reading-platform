package com.soap.soap.application.port.in;

import com.soap.soap.application.command.UpdateReadingProgressCommand;
import com.soap.soap.domain.model.ReadingProgress;

public interface UpdateReadingProgressPort {
  ReadingProgress updateReadingProgress(UpdateReadingProgressCommand command);
}
