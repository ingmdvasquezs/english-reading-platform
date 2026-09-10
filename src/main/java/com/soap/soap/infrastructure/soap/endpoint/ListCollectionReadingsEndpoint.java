package com.soap.soap.infrastructure.soap.endpoint;

import static com.soap.soap.infrastructure.soap.contract.ReadingsSoapContract.NAMESPACE_URI;

import com.soap.soap.application.port.in.ListCollectionReadingsPort;
import com.soap.soap.infrastructure.soap.generated.ListCollectionReadingsRequest;
import com.soap.soap.infrastructure.soap.generated.ListCollectionReadingsResponse;
import com.soap.soap.infrastructure.soap.mapper.CollectionSoapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
@RequiredArgsConstructor
public class ListCollectionReadingsEndpoint {
  private final ListCollectionReadingsPort port;
  private final CollectionSoapMapper mapper;

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "listCollectionReadingsRequest")
  @ResponsePayload
  public ListCollectionReadingsResponse listCollectionReadings(
      @RequestPayload ListCollectionReadingsRequest request) {
    return mapper.toResponse(
        port.listCollectionReadings(request.getCollectionKey(), mapper.toPageRequest(request)));
  }
}
