package com.soap.soap.domain.model;

/**
 * Categorizes the nature of editorial adaptation performed on the source material.
 * TRANSLATED_ADAPTATION requires an explicit sourceLanguage on PLATFORM readings.
 */
public enum AdaptationKind {
  ORIGINAL,
  PEDAGOGICAL_ADAPTATION,
  TRANSLATED_ADAPTATION,
  CURATED_EXCERPT
}
