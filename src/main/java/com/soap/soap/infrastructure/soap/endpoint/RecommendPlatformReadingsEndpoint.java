package com.soap.soap.infrastructure.soap.endpoint;

import static com.soap.soap.infrastructure.soap.contract.ReadingsSoapContract.NAMESPACE_URI;

import com.soap.soap.application.port.in.RecommendPlatformReadingsPort;
import com.soap.soap.infrastructure.soap.generated.RecommendPlatformReadingsRequest;
import com.soap.soap.infrastructure.soap.generated.RecommendPlatformReadingsResponse;
import com.soap.soap.infrastructure.soap.mapper.RecommendPlatformReadingsSoapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
@RequiredArgsConstructor
public class RecommendPlatformReadingsEndpoint {
  private final RecommendPlatformReadingsPort port;
  private final RecommendPlatformReadingsSoapMapper mapper;

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "recommendPlatformReadingsRequest")
  @ResponsePayload
  public RecommendPlatformReadingsResponse recommendPlatformReadings(
      @RequestPayload RecommendPlatformReadingsRequest request) {
    return mapper.toResponse(port.recommendPlatformReadings(mapper.toPageRequest(request)));
  }
}
