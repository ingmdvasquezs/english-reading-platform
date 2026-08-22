package com.soap.soap.infrastructure.soap.endpoint;

import static com.soap.soap.infrastructure.soap.contract.ReadingsSoapContract.NAMESPACE_URI;

import com.soap.soap.application.port.in.RegisterUserPort;
import com.soap.soap.infrastructure.security.PublicSoapOperation;
import com.soap.soap.infrastructure.security.RateLimitPolicy;
import com.soap.soap.infrastructure.security.RateLimited;
import com.soap.soap.infrastructure.soap.generated.RegisterUserRequest;
import com.soap.soap.infrastructure.soap.generated.RegisterUserResponse;
import com.soap.soap.infrastructure.soap.mapper.UserAuthenticationSoapMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.*;

@Endpoint
@RequiredArgsConstructor
public class RegisterUserEndpoint {
  private final RegisterUserPort port;
  private final UserAuthenticationSoapMapper mapper;

  @PayloadRoot(namespace = NAMESPACE_URI, localPart = "registerUserRequest")
  @ResponsePayload
  @PublicSoapOperation
  @RateLimited(RateLimitPolicy.REGISTER)
  public RegisterUserResponse register(@RequestPayload RegisterUserRequest request) {
    return mapper.toResponse(port.registerUser(mapper.toCommand(request)));
  }
}
