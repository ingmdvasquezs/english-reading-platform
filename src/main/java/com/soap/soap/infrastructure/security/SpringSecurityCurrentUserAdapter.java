package com.soap.soap.infrastructure.security;

import com.soap.soap.application.exception.AuthenticationRequiredException;
import com.soap.soap.application.port.out.CurrentUserPort;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class SpringSecurityCurrentUserAdapter implements CurrentUserPort {
  @Override
  public UUID requireUserId() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !authentication.isAuthenticated()
        || !(authentication.getPrincipal() instanceof Jwt jwt)) {
      throw new AuthenticationRequiredException();
    }
    try {
      return UUID.fromString(jwt.getSubject());
    } catch (IllegalArgumentException exception) {
      throw new AuthenticationRequiredException();
    }
  }
}
