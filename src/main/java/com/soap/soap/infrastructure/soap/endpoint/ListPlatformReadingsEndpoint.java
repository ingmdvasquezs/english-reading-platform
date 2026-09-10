package com.soap.soap.infrastructure.soap.endpoint;

import static com.soap.soap.infrastructure.soap.contract.ReadingsSoapContract.NAMESPACE_URI;

import com.soap.soap.application.port.in.ListPlatformReadingsPort;
import com.soap.soap.infrastructure.soap.generated.ListPlatformReadingsRequest;
import com.soap.soap.infrastructure.soap.generated.ListPlatformReadingsResponse;
import com.soap.soap.infrastructure.soap.mapper.ListPlatformReadingsSoapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
@RequiredArgsConstructor
public class ListPlatformReadingsEndpoint {
  private final ListPlatformReadingsPort port;
  private final ListPlatformReadingsSoapMapper mapper;

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "listPlatformReadingsRequest")
  @ResponsePayload
  public ListPlatformReadingsResponse listPlatformReadings(
      @RequestPayload ListPlatformReadingsRequest request) {
    return mapper.toResponse(port.listPlatformReadings(mapper.toPageRequest(request)));
  }
}
