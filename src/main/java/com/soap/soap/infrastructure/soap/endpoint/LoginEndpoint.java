package com.soap.soap.infrastructure.soap.endpoint;

import static com.soap.soap.infrastructure.soap.contract.ReadingsSoapContract.NAMESPACE_URI;

import com.soap.soap.application.port.in.LoginPort;
import com.soap.soap.infrastructure.security.PublicSoapOperation;
import com.soap.soap.infrastructure.security.RateLimitPolicy;
import com.soap.soap.infrastructure.security.RateLimited;
import com.soap.soap.infrastructure.soap.generated.LoginRequest;
import com.soap.soap.infrastructure.soap.generated.LoginResponse;
import com.soap.soap.infrastructure.soap.mapper.UserAuthenticationSoapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.*;

@Endpoint
@RequiredArgsConstructor
public class LoginEndpoint {
  private final LoginPort port;
  private final UserAuthenticationSoapMapper mapper;

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "loginRequest")
  @ResponsePayload
  @PublicSoapOperation
  @RateLimited(RateLimitPolicy.LOGIN)
  public LoginResponse login(@RequestPayload LoginRequest request) {
    return mapper.toResponse(port.login(mapper.toCommand(request)));
  }
}
