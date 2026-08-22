package com.soap.soap.infrastructure.soap.endpoint;

import static com.soap.soap.infrastructure.soap.contract.ReadingsSoapContract.NAMESPACE_URI;

import com.soap.soap.application.port.in.LookupWordPort;
import com.soap.soap.infrastructure.soap.generated.LookupWordRequest;
import com.soap.soap.infrastructure.soap.generated.LookupWordResponse;
import com.soap.soap.infrastructure.soap.mapper.LookupWordSoapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
@RequiredArgsConstructor
public class LookupWordEndpoint {
  private final LookupWordPort port;
  private final LookupWordSoapMapper mapper;

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "lookupWordRequest")
  @ResponsePayload
  public LookupWordResponse lookup(@RequestPayload LookupWordRequest request) {
    return mapper.toResponse(port.lookupWord(mapper.toWord(request)));
  }
}
