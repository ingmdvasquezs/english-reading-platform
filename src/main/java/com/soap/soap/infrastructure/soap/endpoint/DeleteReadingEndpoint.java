package com.soap.soap.infrastructure.soap.endpoint;

import com.soap.soap.application.port.in.DeleteReadingPort;
import com.soap.soap.infrastructure.soap.generated.DeleteReadingRequest;
import com.soap.soap.infrastructure.soap.generated.DeleteReadingResponse;
import com.soap.soap.infrastructure.soap.mapper.DeleteReadingSoapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
@RequiredArgsConstructor
public class DeleteReadingEndpoint {
  private static final String NAMESPACE_URI = "http://soap.com/english-reading/readings";
  private final DeleteReadingPort port;
  private final DeleteReadingSoapMapper mapper;

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "deleteReadingRequest")
  @ResponsePayload
  public DeleteReadingResponse deleteReading(@RequestPayload DeleteReadingRequest request) {
    port.deleteReading(mapper.toReadingId(request));
    return mapper.toResponse();
  }
}
