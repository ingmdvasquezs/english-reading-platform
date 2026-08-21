package com.soap.soap.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class SecurityConfigurationTest {
  private final SecurityConfiguration configuration = new SecurityConfiguration();

  @Test
  void rejectsASecretShorterThan256Bits() {
    assertThatThrownBy(() -> configuration.jwtSecretKey("too-short"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("at least 32 UTF-8 bytes");
  }

  @Test
  void rejectsABlankSecret() {
    assertThatThrownBy(() -> configuration.jwtSecretKey(" "))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must not be blank");
  }

  @Test
  void rejectsAMissingSecret() {
    assertThatThrownBy(() -> configuration.jwtSecretKey(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must be configured");
  }

  @Test
  void usesEveryByteOfAValidSecret() {
    var secret = "á-valid-secret-with-more-than-thirty-two-bytes";
    assertThat(configuration.jwtSecretKey(secret).getEncoded())
        .isEqualTo(secret.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void legacyMigrationPlaceholderIsNotAValidBcryptCredential() {
    var legacy = "{bcrypt}$2a$10$invalidlegacycredentialinvalidlegacycredentialinv";
    assertThat(new BCryptPasswordEncoder().matches("any-password", legacy)).isFalse();
    assertThat(legacy.length()).isLessThanOrEqualTo(100);
  }

  @Test
  void passwordAdapterStoresBcryptRatherThanTheRawPassword() {
    var adapter = new PasswordEncoderAdapter(new BCryptPasswordEncoder());
    var encoded = adapter.encode("secret123");
    assertThat(encoded).isNotEqualTo("secret123").startsWith("$2");
    assertThat(adapter.matches("secret123", encoded)).isTrue();
  }
}
