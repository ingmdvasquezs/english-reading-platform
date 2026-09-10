package com.soap.soap.infrastructure.soap.mapper;

import com.soap.soap.domain.model.Reading;
import com.soap.soap.infrastructure.soap.generated.EditorialLevelType;
import com.soap.soap.infrastructure.soap.generated.GetReadingRequest;
import com.soap.soap.infrastructure.soap.generated.GetReadingResponse;
import com.soap.soap.infrastructure.soap.generated.ReadingOriginType;
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
    if (reading.user() != null) {
      result.setUserId(reading.user().id().toString());
    }
    result.setTitle(reading.title());
    result.setContent(reading.content());
    result.setLanguage(reading.language());
    result.setOrigin(ReadingOriginType.valueOf(reading.origin().name()));
    if (reading.editorialLevel() != null) {
      result.setEditorialLevel(EditorialLevelType.fromValue(reading.editorialLevel().name()));
    }
    result.setCategory(reading.category());
    result.setCreatedAt(toXmlDate(reading.createdAt()));
    return result;
  }
}
