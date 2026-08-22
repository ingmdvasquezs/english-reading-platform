package com.soap.soap.infrastructure.soap.endpoint;

import static com.soap.soap.infrastructure.soap.contract.ReadingsSoapContract.NAMESPACE_URI;

import com.soap.soap.application.port.in.GetReadingReaderDataPort;
import com.soap.soap.infrastructure.soap.generated.GetReadingReaderDataRequest;
import com.soap.soap.infrastructure.soap.generated.GetReadingReaderDataResponse;
import com.soap.soap.infrastructure.soap.mapper.GetReadingReaderDataSoapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
@RequiredArgsConstructor
public class GetReadingReaderDataEndpoint {
  private final GetReadingReaderDataPort port;
  private final GetReadingReaderDataSoapMapper mapper;

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "getReadingReaderDataRequest")
  @ResponsePayload
  public GetReadingReaderDataResponse getReadingReaderData(
      @RequestPayload GetReadingReaderDataRequest request) {
    return mapper.toResponse(port.getReadingReaderData(mapper.toReadingId(request)));
  }
}
