package com.soap.soap.infrastructure.soap.endpoint;

import static com.soap.soap.infrastructure.soap.contract.ReadingsSoapContract.NAMESPACE_URI;

import com.soap.soap.application.port.in.SetVocabularyStatusPort;
import com.soap.soap.infrastructure.soap.generated.SetVocabularyStatusRequest;
import com.soap.soap.infrastructure.soap.generated.SetVocabularyStatusResponse;
import com.soap.soap.infrastructure.soap.mapper.SetVocabularyStatusSoapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
@RequiredArgsConstructor
public class SetVocabularyStatusEndpoint {
  private final SetVocabularyStatusPort port;
  private final SetVocabularyStatusSoapMapper mapper;

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "setVocabularyStatusRequest")
  @ResponsePayload
  public SetVocabularyStatusResponse setVocabularyStatus(
      @RequestPayload SetVocabularyStatusRequest request) {
    return mapper.toResponse(port.setVocabularyStatus(mapper.toCommand(request)));
  }
}
