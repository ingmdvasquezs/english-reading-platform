package com.soap.soap.domain.model;

/**
 * Categorizes the origin medium or format of the underlying source. Decoupled from rights status
 * and adaptation kind.
 */
public enum SourceKind {
  ORAL_TRADITION,
  HISTORICAL_SOURCE,
  LITERARY_WORK,
  FACTUAL_REFERENCE,
  ORIGINAL_EDITORIAL
}
