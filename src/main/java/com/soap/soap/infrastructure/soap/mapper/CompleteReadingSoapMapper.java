package com.soap.soap.infrastructure.soap.mapper;

import com.soap.soap.domain.model.ReadingProgress;
import com.soap.soap.infrastructure.soap.generated.CompleteReadingRequest;
import com.soap.soap.infrastructure.soap.generated.CompleteReadingResponse;
import com.soap.soap.infrastructure.soap.generated.ReadingProgressStatusType;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CompleteReadingSoapMapper extends SoapMapperSupport {
  public UUID toReadingId(CompleteReadingRequest request) {
    return parseUuid(request.getReadingId(), "readingId");
  }

  public CompleteReadingResponse toResponse(ReadingProgress progress) {
    var response = new CompleteReadingResponse();
    response.setReadingId(progress.readingId().toString());
    response.setStatus(ReadingProgressStatusType.fromValue(progress.status().name()));
    response.setStartedAt(toXmlDate(progress.startedAt()));
    response.setCompletedAt(toXmlDate(progress.completedAt()));
    return response;
  }
}
