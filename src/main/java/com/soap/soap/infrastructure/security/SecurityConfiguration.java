package com.soap.soap.infrastructure.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfiguration {
  @Bean
  FilterRegistrationBean<RequestSizeLimitFilter> requestSizeFilterRegistration(
      RequestSizeLimitFilter filter) {
    var registration = new FilterRegistrationBean<>(filter);
    registration.setName("requestSizeLimitFilter");
    registration.addUrlPatterns("/ws/*");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
    return registration;
  }

  @Bean
  FilterRegistrationBean<JwtAuthenticationFilter> disableStandaloneJwtFilterRegistration(
      JwtAuthenticationFilter filter) {
    // JWT belongs exclusively to the Spring Security chain, immediately before username/password.
    var registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  SecretKey jwtSecretKey(@Value("${security.jwt.secret}") String secret) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException("JWT_SECRET must be configured and must not be blank");
    }
    var bytes = secret.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < 32) {
      throw new IllegalStateException("JWT_SECRET must contain at least 32 UTF-8 bytes for HS256");
    }
    return new SecretKeySpec(bytes, "HmacSHA256");
  }

  @Bean
  JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
  }

  @Bean
  JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
    return NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter)
      throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // SOAP login and protected operations share /ws. Operation-level authentication is
        // enforced by SoapSecurityInterceptor without parsing XML in this HTTP filter chain.
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers(
                        "/ws/**", "/actuator/health/**", "/actuator/info", "/actuator/prometheus")
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
