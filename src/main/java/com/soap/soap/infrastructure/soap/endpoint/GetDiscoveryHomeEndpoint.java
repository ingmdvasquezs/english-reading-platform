package com.soap.soap.infrastructure.soap.endpoint;

import static com.soap.soap.infrastructure.soap.contract.ReadingsSoapContract.NAMESPACE_URI;

import com.soap.soap.application.port.in.GetDiscoveryHomePort;
import com.soap.soap.infrastructure.soap.generated.GetDiscoveryHomeRequest;
import com.soap.soap.infrastructure.soap.generated.GetDiscoveryHomeResponse;
import com.soap.soap.infrastructure.soap.mapper.GetDiscoveryHomeSoapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
@RequiredArgsConstructor
public class GetDiscoveryHomeEndpoint {

  private final GetDiscoveryHomePort port;
  private final GetDiscoveryHomeSoapMapper mapper;

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "getDiscoveryHomeRequest")
  @ResponsePayload
  public GetDiscoveryHomeResponse getDiscoveryHome(
      @RequestPayload GetDiscoveryHomeRequest request) {
    var query = mapper.toQuery(request);
    var result = port.getDiscoveryHome(query);
    return mapper.toResponse(result);
  }
}
