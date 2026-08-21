package com.soap.soap.infrastructure.soap.endpoint;

import static com.soap.soap.infrastructure.soap.contract.ReadingsSoapContract.NAMESPACE_URI;

import com.soap.soap.application.port.in.GetReadingPort;
import com.soap.soap.infrastructure.soap.generated.GetReadingRequest;
import com.soap.soap.infrastructure.soap.generated.GetReadingResponse;
import com.soap.soap.infrastructure.soap.mapper.GetReadingSoapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
@RequiredArgsConstructor
public class GetReadingEndpoint {
  private final GetReadingPort port;
  private final GetReadingSoapMapper mapper;

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "getReadingRequest")
  @ResponsePayload
  public GetReadingResponse getReading(@RequestPayload GetReadingRequest request) {
    return mapper.toResponse(port.getReading(mapper.toReadingId(request)));
  }
}
