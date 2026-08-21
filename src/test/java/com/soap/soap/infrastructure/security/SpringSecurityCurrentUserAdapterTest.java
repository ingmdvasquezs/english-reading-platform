package com.soap.soap.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soap.soap.application.exception.AuthenticationRequiredException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

class SpringSecurityCurrentUserAdapterTest {
  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void protectedOperationsWithoutAuthenticationFailExplicitly() {
    assertThatThrownBy(() -> new SpringSecurityCurrentUserAdapter().requireUserId())
        .isInstanceOf(AuthenticationRequiredException.class)
        .hasMessage("Authentication is required");
  }
}
