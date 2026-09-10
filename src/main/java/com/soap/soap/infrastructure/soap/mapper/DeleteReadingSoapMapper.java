package com.soap.soap.infrastructure.soap.mapper;

import com.soap.soap.infrastructure.soap.generated.DeleteReadingRequest;
import com.soap.soap.infrastructure.soap.generated.DeleteReadingResponse;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DeleteReadingSoapMapper extends SoapMapperSupport {
  public UUID toReadingId(DeleteReadingRequest request) {
    return parseUuid(request.getReadingId(), "readingId");
  }

  public DeleteReadingResponse toResponse() {
    var response = new DeleteReadingResponse();
    response.setSuccess(true);
    return response;
  }
}
