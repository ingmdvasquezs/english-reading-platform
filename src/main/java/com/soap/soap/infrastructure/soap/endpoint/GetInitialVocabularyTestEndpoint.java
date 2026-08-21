package com.soap.soap.infrastructure.soap.endpoint;

import static com.soap.soap.infrastructure.soap.contract.ReadingsSoapContract.NAMESPACE_URI;

import com.soap.soap.application.port.in.GetInitialVocabularyTestPort;
import com.soap.soap.infrastructure.soap.generated.GetInitialVocabularyTestRequest;
import com.soap.soap.infrastructure.soap.generated.GetInitialVocabularyTestResponse;
import com.soap.soap.infrastructure.soap.mapper.InitialVocabularyTestSoapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
@RequiredArgsConstructor
public class GetInitialVocabularyTestEndpoint {
  private final GetInitialVocabularyTestPort port;
  private final InitialVocabularyTestSoapMapper mapper;

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "getInitialVocabularyTestRequest")
  @ResponsePayload
  public GetInitialVocabularyTestResponse get(
      @RequestPayload GetInitialVocabularyTestRequest request) {
    return mapper.toResponse(port.getInitialVocabularyTest());
  }
}
