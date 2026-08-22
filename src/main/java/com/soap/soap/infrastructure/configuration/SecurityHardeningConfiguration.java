package com.soap.soap.infrastructure.configuration;

import com.soap.soap.application.model.InputLimits;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityHardeningConfiguration {
  @Bean
  InputLimits inputLimits(
      @Value("${app.input.max-name-characters:100}") int maxNameCharacters,
      @Value("${app.input.max-email-characters:254}") int maxEmailCharacters,
      @Value("${app.input.max-password-characters:128}") int maxPasswordCharacters,
      @Value("${app.input.max-title-characters:200}") int maxTitleCharacters,
      @Value("${app.input.max-reading-content-bytes:1048576}") int maxReadingContentBytes,
      @Value("${app.input.max-word-characters:100}") int maxWordCharacters,
      @Value("${app.input.max-onboarding-words:100}") int maxOnboardingWords) {
    return new InputLimits(
        maxNameCharacters,
        maxEmailCharacters,
        maxPasswordCharacters,
        maxTitleCharacters,
        maxReadingContentBytes,
        maxWordCharacters,
        maxOnboardingWords);
  }
}
