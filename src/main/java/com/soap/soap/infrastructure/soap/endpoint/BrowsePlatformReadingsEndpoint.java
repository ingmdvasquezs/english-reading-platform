package com.soap.soap.infrastructure.soap.endpoint;

import static com.soap.soap.infrastructure.soap.contract.ReadingsSoapContract.NAMESPACE_URI;

import com.soap.soap.application.port.in.BrowsePlatformReadingsPort;
import com.soap.soap.infrastructure.soap.generated.BrowsePlatformReadingsRequest;
import com.soap.soap.infrastructure.soap.generated.BrowsePlatformReadingsResponse;
import com.soap.soap.infrastructure.soap.mapper.BrowsePlatformReadingsSoapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
@RequiredArgsConstructor
public class BrowsePlatformReadingsEndpoint {
  private final BrowsePlatformReadingsPort port;
  private final BrowsePlatformReadingsSoapMapper mapper;

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "browsePlatformReadingsRequest")
  @ResponsePayload
  public BrowsePlatformReadingsResponse browsePlatformReadings(
      @RequestPayload BrowsePlatformReadingsRequest request) {
    var query = mapper.toQuery(request);
    return mapper.toResponse(port.browsePlatformReadings(query));
  }
}
