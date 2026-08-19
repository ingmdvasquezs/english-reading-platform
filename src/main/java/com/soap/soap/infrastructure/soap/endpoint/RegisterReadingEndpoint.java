package com.soap.soap.infrastructure.soap.endpoint;

import com.soap.soap.application.port.in.RegisterReadingPort;
import com.soap.soap.infrastructure.soap.generated.RegisterReadingRequest;
import com.soap.soap.infrastructure.soap.generated.RegisterReadingResponse;
import com.soap.soap.infrastructure.soap.mapper.RegisterReadingSoapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
@RequiredArgsConstructor
public class RegisterReadingEndpoint {

  private static final String NAMESPACE_URI = "http://soap.com/english-reading/readings";

  private final RegisterReadingPort registerReadingPort;
  private final RegisterReadingSoapMapper mapper;

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "registerReadingRequest")
  @ResponsePayload
  public RegisterReadingResponse registerReading(@RequestPayload RegisterReadingRequest request) {

    var command = mapper.toCommand(request);
    var reading = registerReadingPort.registerReading(command);

    return mapper.toResponse(reading);
  }
}
