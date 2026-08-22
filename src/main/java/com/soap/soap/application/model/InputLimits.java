package com.soap.soap.application.model;

public record InputLimits(
    int maxNameCharacters,
    int maxEmailCharacters,
    int maxPasswordCharacters,
    int maxTitleCharacters,
    int maxReadingContentBytes,
    int maxWordCharacters,
    int maxOnboardingWords) {

  public static InputLimits defaults() {
    return new InputLimits(100, 254, 128, 200, 1_048_576, 100, 100);
  }
}
