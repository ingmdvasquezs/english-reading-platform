package com.soap.soap.infrastructure.soap.endpoint;

import static com.soap.soap.infrastructure.soap.contract.ReadingsSoapContract.NAMESPACE_URI;

import com.soap.soap.application.port.in.ListContinueReadingPort;
import com.soap.soap.infrastructure.soap.generated.ListContinueReadingRequest;
import com.soap.soap.infrastructure.soap.generated.ListContinueReadingResponse;
import com.soap.soap.infrastructure.soap.mapper.ListContinueReadingSoapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
@RequiredArgsConstructor
public class ListContinueReadingEndpoint {
  private final ListContinueReadingPort port;
  private final ListContinueReadingSoapMapper mapper;

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "listContinueReadingRequest")
  @ResponsePayload
  public ListContinueReadingResponse listContinueReading(
      @RequestPayload ListContinueReadingRequest request) {
    return mapper.toResponse(port.listContinueReading(mapper.toPageRequest(request)));
  }
}
