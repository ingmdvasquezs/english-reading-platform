package com.soap.soap.infrastructure.soap.endpoint;

import static com.soap.soap.infrastructure.soap.contract.ReadingsSoapContract.NAMESPACE_URI;

import com.soap.soap.application.port.in.ListUserReadingsPort;
import com.soap.soap.infrastructure.soap.generated.ListUserReadingsRequest;
import com.soap.soap.infrastructure.soap.generated.ListUserReadingsResponse;
import com.soap.soap.infrastructure.soap.mapper.ListUserReadingsSoapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
@RequiredArgsConstructor
public class ListUserReadingsEndpoint {
  private final ListUserReadingsPort port;
  private final ListUserReadingsSoapMapper mapper;

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "listUserReadingsRequest")
  @ResponsePayload
  public ListUserReadingsResponse listUserReadings(
      @RequestPayload ListUserReadingsRequest request) {
    return mapper.toResponse(port.listUserReadings(mapper.toPageRequest(request)));
  }
}
