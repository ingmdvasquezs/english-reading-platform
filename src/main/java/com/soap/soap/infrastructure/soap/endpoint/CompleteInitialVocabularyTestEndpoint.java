package com.soap.soap.infrastructure.soap.endpoint;

import static com.soap.soap.infrastructure.soap.contract.ReadingsSoapContract.NAMESPACE_URI;

import com.soap.soap.application.port.in.CompleteInitialVocabularyTestPort;
import com.soap.soap.infrastructure.soap.generated.CompleteInitialVocabularyTestRequest;
import com.soap.soap.infrastructure.soap.generated.CompleteInitialVocabularyTestResponse;
import com.soap.soap.infrastructure.soap.mapper.InitialVocabularyTestSoapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
@RequiredArgsConstructor
public class CompleteInitialVocabularyTestEndpoint {
  private final CompleteInitialVocabularyTestPort port;
  private final InitialVocabularyTestSoapMapper mapper;

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "completeInitialVocabularyTestRequest")
  @ResponsePayload
  public CompleteInitialVocabularyTestResponse complete(
      @RequestPayload CompleteInitialVocabularyTestRequest request) {
    return mapper.toResponse(
        port.completeInitialVocabularyTest(mapper.testId(request), request.getKnownWords()));
  }
}
