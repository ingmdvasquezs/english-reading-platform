package com.soap.soap.infrastructure.soap.endpoint;

import static com.soap.soap.infrastructure.soap.contract.ReadingsSoapContract.NAMESPACE_URI;

import com.soap.soap.application.port.in.GetLatestComprehensionResultPort;
import com.soap.soap.infrastructure.soap.generated.GetLatestComprehensionResultRequest;
import com.soap.soap.infrastructure.soap.generated.GetLatestComprehensionResultResponse;
import com.soap.soap.infrastructure.soap.mapper.ComprehensionSoapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
@RequiredArgsConstructor
public class GetLatestComprehensionResultEndpoint {
  private final GetLatestComprehensionResultPort port;
  private final ComprehensionSoapMapper mapper;

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "getLatestComprehensionResultRequest")
  @ResponsePayload
  public GetLatestComprehensionResultResponse getLatestResult(
      @RequestPayload GetLatestComprehensionResultRequest request) {
    return mapper.toLatestResultResponse(port.getLatestResult(mapper.toLatestReadingId(request)));
  }
}
