package com.soap.soap.infrastructure.soap.endpoint;

import static com.soap.soap.infrastructure.soap.contract.ReadingsSoapContract.NAMESPACE_URI;

import com.soap.soap.application.port.in.SubmitComprehensionAttemptPort;
import com.soap.soap.infrastructure.soap.generated.SubmitComprehensionAttemptRequest;
import com.soap.soap.infrastructure.soap.generated.SubmitComprehensionAttemptResponse;
import com.soap.soap.infrastructure.soap.mapper.ComprehensionSoapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

@Endpoint
@RequiredArgsConstructor
public class SubmitComprehensionAttemptEndpoint {
  private final SubmitComprehensionAttemptPort port;
  private final ComprehensionSoapMapper mapper;

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "submitComprehensionAttemptRequest")
  @ResponsePayload
  public SubmitComprehensionAttemptResponse submitAttempt(
      @RequestPayload SubmitComprehensionAttemptRequest request) {
    return mapper.toSubmitResponse(port.submitAttempt(mapper.toCommand(request)));
  }
}
