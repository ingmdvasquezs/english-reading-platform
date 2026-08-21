package com.soap.soap.infrastructure.security;

import com.soap.soap.application.model.AccessToken;
import com.soap.soap.application.port.out.TokenProviderPort;
import com.soap.soap.domain.model.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtTokenProviderAdapter implements TokenProviderPort {
  private final JwtEncoder encoder;
  private final Clock clock;

  @Value("${security.jwt.expiration}")
  private Duration expiration;

  @Override
  public AccessToken create(User user) {
    var issuedAt = Instant.now(clock);
    var claims =
        JwtClaimsSet.builder()
            .subject(user.id().toString())
            .claim("email", user.email())
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plus(expiration))
            .build();
    var header = JwsHeader.with(MacAlgorithm.HS256).build();
    var value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    return new AccessToken(value, "Bearer", expiration.toSeconds());
  }
}
