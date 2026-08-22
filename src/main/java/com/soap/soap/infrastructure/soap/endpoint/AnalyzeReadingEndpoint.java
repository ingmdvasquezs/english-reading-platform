package com.soap.soap.infrastructure.soap.endpoint;

import static com.soap.soap.infrastructure.soap.contract.ReadingsSoapContract.NAMESPACE_URI;

import com.soap.soap.application.port.in.AnalyzeReadingPort;
import com.soap.soap.infrastructure.security.RateLimitPolicy;
import com.soap.soap.infrastructure.security.RateLimited;
import com.soap.soap.infrastructure.soap.generated.AnalyzeReadingRequest;
import com.soap.soap.infrastructure.soap.generated.AnalyzeReadingResponse;
import com.soap.soap.infrastructure.soap.mapper.AnalyzeReadingSoapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
@RequiredArgsConstructor
public class AnalyzeReadingEndpoint {
  private final AnalyzeReadingPort port;
  private final AnalyzeReadingSoapMapper mapper;

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "analyzeReadingRequest")
  @ResponsePayload
  @RateLimited(RateLimitPolicy.ANALYZE)
  public AnalyzeReadingResponse analyzeReading(@RequestPayload AnalyzeReadingRequest request) {
    return mapper.toResponse(port.analyzeReading(mapper.toReadingId(request)));
  }
}
