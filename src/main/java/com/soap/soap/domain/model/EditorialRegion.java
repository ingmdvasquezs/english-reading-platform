package com.soap.soap.domain.model;

/**
 * Geographical region of origin or setting for editorial content. Disentangled from cultural
 * collections (e.g. "Latin America" is a collection, not a region). GLOBAL designates content
 * without a specific local geographical anchor.
 */
public enum EditorialRegion {
  SOUTH_AMERICA,
  CENTRAL_AMERICA,
  CARIBBEAN,
  NORTHERN_AMERICA,
  EUROPE,
  EAST_ASIA,
  SOUTHEAST_ASIA,
  SOUTH_ASIA,
  CENTRAL_ASIA,
  MIDDLE_EAST,
  NORTH_AFRICA,
  SUB_SAHARAN_AFRICA,
  OCEANIA,
  GLOBAL
}
