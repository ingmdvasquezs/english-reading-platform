package com.soap.soap.infrastructure.soap.mapper;

import com.soap.soap.application.model.ReadingReaderData;
import com.soap.soap.infrastructure.soap.generated.GetReadingReaderDataRequest;
import com.soap.soap.infrastructure.soap.generated.GetReadingReaderDataResponse;
import com.soap.soap.infrastructure.soap.generated.ReaderTokenType;
import com.soap.soap.infrastructure.soap.generated.ReaderTokenTypeType;
import com.soap.soap.infrastructure.soap.generated.VocabularyStatusType;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class GetReadingReaderDataSoapMapper extends SoapMapperSupport {
  public UUID toReadingId(GetReadingReaderDataRequest request) {
    return parseUuid(request.getReadingId(), "readingId");
  }

  public GetReadingReaderDataResponse toResponse(ReadingReaderData data) {
    var response = new GetReadingReaderDataResponse();
    response.setReadingId(data.readingId().toString());
    response.setTitle(data.title());
    response.setLanguage(data.language());
    data.tokens()
        .forEach(
            token -> {
              var soapToken = new ReaderTokenType();
              soapToken.setValue(token.value());
              soapToken.setNormalizedValue(token.normalizedValue());
              soapToken.setType(ReaderTokenTypeType.valueOf(token.type().name()));
              if (token.status() != null) {
                soapToken.setStatus(VocabularyStatusType.valueOf(token.status().name()));
              }
              response.getTokens().add(soapToken);
            });
    return response;
  }
}
