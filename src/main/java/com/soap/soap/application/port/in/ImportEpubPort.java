package com.soap.soap.application.port.in;

import com.soap.soap.application.command.ImportEpubCommand;
import com.soap.soap.domain.model.ImportedDocument;

public interface ImportEpubPort {
  ImportedDocument importEpub(ImportEpubCommand command);
}
