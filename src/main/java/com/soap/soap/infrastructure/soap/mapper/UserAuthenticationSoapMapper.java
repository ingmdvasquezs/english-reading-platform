package com.soap.soap.infrastructure.soap.mapper;

import com.soap.soap.application.command.LoginCommand;
import com.soap.soap.application.command.RegisterUserCommand;
import com.soap.soap.application.model.AccessToken;
import com.soap.soap.domain.model.User;
import com.soap.soap.infrastructure.soap.generated.LoginRequest;
import com.soap.soap.infrastructure.soap.generated.LoginResponse;
import com.soap.soap.infrastructure.soap.generated.RegisterUserRequest;
import com.soap.soap.infrastructure.soap.generated.RegisterUserResponse;
import java.time.ZoneOffset;
import javax.xml.datatype.DatatypeFactory;
import org.springframework.stereotype.Component;

@Component
public class UserAuthenticationSoapMapper {
  public RegisterUserCommand toCommand(RegisterUserRequest request) {
    return new RegisterUserCommand(request.getName(), request.getEmail(), request.getPassword());
  }

  public LoginCommand toCommand(LoginRequest request) {
    return new LoginCommand(request.getEmail(), request.getPassword());
  }

  public RegisterUserResponse toResponse(User user) {
    var response = new RegisterUserResponse();
    response.setUserId(user.id().toString());
    response.setName(user.name());
    response.setEmail(user.email());
    try {
      response.setCreatedAt(
          DatatypeFactory.newInstance()
              .newXMLGregorianCalendar(user.createdAt().atOffset(ZoneOffset.UTC).toString()));
    } catch (javax.xml.datatype.DatatypeConfigurationException exception) {
      throw new IllegalStateException("Cannot map timestamp", exception);
    }
    return response;
  }

  public LoginResponse toResponse(AccessToken token) {
    var response = new LoginResponse();
    response.setAccessToken(token.value());
    response.setTokenType(token.tokenType());
    response.setExpiresIn(token.expiresIn());
    return response;
  }
}
