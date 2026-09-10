package com.soap.soap.infrastructure.soap.endpoint;

import com.soap.soap.application.port.in.CompleteReadingPort;
import com.soap.soap.infrastructure.soap.generated.CompleteReadingRequest;
import com.soap.soap.infrastructure.soap.generated.CompleteReadingResponse;
import com.soap.soap.infrastructure.soap.mapper.CompleteReadingSoapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
@RequiredArgsConstructor
public class CompleteReadingEndpoint {
  private static final String NAMESPACE_URI = "http://soap.com/english-reading/readings";
  private final CompleteReadingPort port;
  private final CompleteReadingSoapMapper mapper;

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "completeReadingRequest")
  @ResponsePayload
  public CompleteReadingResponse completeReading(@RequestPayload CompleteReadingRequest request) {
    return mapper.toResponse(port.completeReading(mapper.toReadingId(request)));
  }
}
