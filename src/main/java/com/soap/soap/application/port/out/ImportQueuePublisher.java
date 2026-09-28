package com.soap.soap.application.port.out;

import com.soap.soap.application.model.DocumentImportQueueMessage;

public interface ImportQueuePublisher {

  void publish(DocumentImportQueueMessage message);
}
