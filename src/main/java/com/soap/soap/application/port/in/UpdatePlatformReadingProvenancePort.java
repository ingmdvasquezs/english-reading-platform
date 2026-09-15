package com.soap.soap.application.port.in;

import com.soap.soap.application.command.UpdatePlatformReadingProvenanceCommand;
import com.soap.soap.domain.model.Reading;

public interface UpdatePlatformReadingProvenancePort {
  Reading updateProvenance(UpdatePlatformReadingProvenanceCommand command);
}
