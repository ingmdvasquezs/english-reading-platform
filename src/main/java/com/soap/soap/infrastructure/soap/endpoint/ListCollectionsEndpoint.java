package com.soap.soap.infrastructure.soap.endpoint;

import static com.soap.soap.infrastructure.soap.contract.ReadingsSoapContract.NAMESPACE_URI;

import com.soap.soap.application.port.in.ListCollectionsPort;
import com.soap.soap.infrastructure.soap.generated.ListCollectionsRequest;
import com.soap.soap.infrastructure.soap.generated.ListCollectionsResponse;
import com.soap.soap.infrastructure.soap.mapper.CollectionSoapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
@RequiredArgsConstructor
public class ListCollectionsEndpoint {
  private final ListCollectionsPort port;
  private final CollectionSoapMapper mapper;

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "listCollectionsRequest")
  @ResponsePayload
  public ListCollectionsResponse listCollections(@RequestPayload ListCollectionsRequest request) {
    return mapper.toResponse(port.listCollections());
  }
}
