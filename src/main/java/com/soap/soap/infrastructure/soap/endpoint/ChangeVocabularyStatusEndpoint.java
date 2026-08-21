package com.soap.soap.infrastructure.soap.endpoint;

import static com.soap.soap.infrastructure.soap.contract.ReadingsSoapContract.NAMESPACE_URI;

import com.soap.soap.application.port.in.ChangeVocabularyStatusPort;
import com.soap.soap.infrastructure.soap.generated.ChangeVocabularyStatusRequest;
import com.soap.soap.infrastructure.soap.generated.ChangeVocabularyStatusResponse;
import com.soap.soap.infrastructure.soap.mapper.ChangeVocabularyStatusSoapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
@RequiredArgsConstructor
public class ChangeVocabularyStatusEndpoint {
  private final ChangeVocabularyStatusPort port;
  private final ChangeVocabularyStatusSoapMapper mapper;

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "changeVocabularyStatusRequest")
  @ResponsePayload
  public ChangeVocabularyStatusResponse changeVocabularyStatus(
      @RequestPayload ChangeVocabularyStatusRequest request) {
    return mapper.toResponse(
        port.changeVocabularyStatus(mapper.toWordId(request), mapper.toStatus(request)));
  }
}
