package com.soap.soap.infrastructure.soap.endpoint;

import static com.soap.soap.infrastructure.soap.contract.ReadingsSoapContract.NAMESPACE_URI;

import com.soap.soap.application.port.in.GetDiscoveryRegionOverviewPort;
import com.soap.soap.infrastructure.soap.generated.GetDiscoveryRegionOverviewRequest;
import com.soap.soap.infrastructure.soap.generated.GetDiscoveryRegionOverviewResponse;
import com.soap.soap.infrastructure.soap.mapper.GetDiscoveryRegionOverviewSoapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
@RequiredArgsConstructor
public class GetDiscoveryRegionOverviewEndpoint {

  private final GetDiscoveryRegionOverviewPort port;
  private final GetDiscoveryRegionOverviewSoapMapper mapper;

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "getDiscoveryRegionOverviewRequest")
  @ResponsePayload
  public GetDiscoveryRegionOverviewResponse getDiscoveryRegionOverview(
      @RequestPayload GetDiscoveryRegionOverviewRequest request) {
    var result = port.getOverview(request.getRegionKey());
    return mapper.toResponse(result);
  }
}
