package com.soap.soap.application.port.in;

import com.soap.soap.application.command.UpdatePublishedEditorialContentCommand;
import com.soap.soap.application.model.UpdatePublishedEditorialContentResult;

public interface UpdatePublishedEditorialContentPort {
  UpdatePublishedEditorialContentResult updateContent(
      UpdatePublishedEditorialContentCommand command);
}
