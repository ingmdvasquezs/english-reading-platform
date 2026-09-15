package com.soap.soap.application.port.in;

import com.soap.soap.application.command.IngestEditorialReadingCommand;
import com.soap.soap.application.model.IngestEditorialReadingResult;

public interface IngestEditorialReadingPort {
  IngestEditorialReadingResult ingest(IngestEditorialReadingCommand command);
}
