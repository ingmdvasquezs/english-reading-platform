package com.soap.soap.infrastructure.soap.mapper;

import com.soap.soap.domain.model.Reading;
import com.soap.soap.infrastructure.soap.generated.GetReadingRequest;
import com.soap.soap.infrastructure.soap.generated.GetReadingResponse;
import com.soap.soap.infrastructure.soap.generated.ReadingType;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class GetReadingSoapMapper extends SoapMapperSupport {
  public UUID toReadingId(GetReadingRequest request) {
    return parseUuid(request.getReadingId(), "readingId");
  }

  public GetReadingResponse toResponse(Reading reading) {
    var response = new GetReadingResponse();
    response.setReading(toReadingType(reading));
    return response;
  }

  ReadingType toReadingType(Reading reading) {
    var result = new ReadingType();
    result.setReadingId(reading.id().toString());
    result.setUserId(reading.user().id().toString());
    result.setTitle(reading.title());
    result.setContent(reading.content());
    result.setLanguage(reading.language());
    result.setCreatedAt(toXmlDate(reading.createdAt()));
    return result;
  }
}
