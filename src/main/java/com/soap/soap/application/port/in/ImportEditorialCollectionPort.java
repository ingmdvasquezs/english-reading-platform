package com.soap.soap.application.port.in;

import com.soap.soap.application.command.ImportEditorialCollectionCommand;
import com.soap.soap.application.model.ImportEditorialCollectionResult;

public interface ImportEditorialCollectionPort {
  ImportEditorialCollectionResult importCollection(ImportEditorialCollectionCommand command);
}
