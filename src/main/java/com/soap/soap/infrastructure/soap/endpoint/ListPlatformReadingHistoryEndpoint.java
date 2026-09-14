package com.soap.soap.infrastructure.soap.endpoint;

import static com.soap.soap.infrastructure.soap.contract.ReadingsSoapContract.NAMESPACE_URI;

import com.soap.soap.application.port.in.ListPlatformReadingHistoryPort;
import com.soap.soap.infrastructure.soap.generated.ListPlatformReadingHistoryRequest;
import com.soap.soap.infrastructure.soap.generated.ListPlatformReadingHistoryResponse;
import com.soap.soap.infrastructure.soap.mapper.ListPlatformReadingHistorySoapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
@RequiredArgsConstructor
public class ListPlatformReadingHistoryEndpoint {
  private final ListPlatformReadingHistoryPort port;
  private final ListPlatformReadingHistorySoapMapper mapper;

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "listPlatformReadingHistoryRequest")
  @ResponsePayload
  public ListPlatformReadingHistoryResponse listPlatformReadingHistory(
      @RequestPayload ListPlatformReadingHistoryRequest request) {
    return mapper.toResponse(port.listPlatformReadingHistory(mapper.toPageRequest(request)));
  }
}
