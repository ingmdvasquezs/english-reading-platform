package com.soap.soap.infrastructure.soap.endpoint;

import static com.soap.soap.infrastructure.soap.contract.ReadingsSoapContract.NAMESPACE_URI;

import com.soap.soap.application.port.in.UpdateReadingProgressPort;
import com.soap.soap.infrastructure.security.RateLimitPolicy;
import com.soap.soap.infrastructure.security.RateLimited;
import com.soap.soap.infrastructure.soap.generated.UpdateReadingProgressRequest;
import com.soap.soap.infrastructure.soap.generated.UpdateReadingProgressResponse;
import com.soap.soap.infrastructure.soap.mapper.UpdateReadingProgressSoapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
@RequiredArgsConstructor
public class UpdateReadingProgressEndpoint {
  private final UpdateReadingProgressPort port;
  private final UpdateReadingProgressSoapMapper mapper;

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "updateReadingProgressRequest")
  @ResponsePayload
  @RateLimited(RateLimitPolicy.READER)
  public UpdateReadingProgressResponse updateReadingProgress(
      @RequestPayload UpdateReadingProgressRequest request) {
    return mapper.toResponse(port.updateReadingProgress(mapper.toCommand(request)));
  }
}
