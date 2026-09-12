package com.soap.soap.infrastructure.soap.endpoint;

import static com.soap.soap.infrastructure.soap.contract.ReadingsSoapContract.NAMESPACE_URI;

import com.soap.soap.application.port.in.GetReadingComprehensionQuizPort;
import com.soap.soap.infrastructure.soap.generated.GetReadingComprehensionQuizRequest;
import com.soap.soap.infrastructure.soap.generated.GetReadingComprehensionQuizResponse;
import com.soap.soap.infrastructure.soap.mapper.ComprehensionSoapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
@RequiredArgsConstructor
public class GetReadingComprehensionQuizEndpoint {
  private final GetReadingComprehensionQuizPort port;
  private final ComprehensionSoapMapper mapper;

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "getReadingComprehensionQuizRequest")
  @ResponsePayload
  public GetReadingComprehensionQuizResponse getQuiz(
      @RequestPayload GetReadingComprehensionQuizRequest request) {
    return mapper.toQuizResponse(port.getQuiz(mapper.toReadingId(request)));
  }
}
