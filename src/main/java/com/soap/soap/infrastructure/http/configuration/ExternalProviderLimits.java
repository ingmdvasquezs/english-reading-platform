package com.soap.soap.infrastructure.http.configuration;

public record ExternalProviderLimits(
    int dictionaryBodyBytes,
    int translationBodyBytes,
    int maximumEntries,
    int maximumMeanings,
    int maximumDefinitionCharacters,
    int maximumExampleCharacters,
    int maximumTranslationCharacters,
    int maximumPhoneticCharacters,
    int maximumAudioUrlCharacters) {

  public static ExternalProviderLimits defaults() {
    return new ExternalProviderLimits(1_048_576, 262_144, 5, 20, 2_000, 2_000, 10_000, 200, 2_048);
  }
}
