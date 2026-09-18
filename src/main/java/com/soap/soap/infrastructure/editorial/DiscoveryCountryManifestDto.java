package com.soap.soap.infrastructure.editorial;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DiscoveryCountryManifestDto(
    @JsonProperty("schemaVersion") Integer schemaVersion,
    @JsonProperty("regionKey") String regionKey,
    @JsonProperty("countryCode") String countryCode,
    @JsonProperty("displayName") String displayName,
    @JsonProperty("tagline") String tagline,
    @JsonProperty("description") String description,
    @JsonProperty("displayOrder") Integer displayOrder,
    @JsonProperty("active") Boolean active,
    @JsonProperty("heroImages") List<HeroImageDto> heroImages) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record HeroImageDto(
      @JsonProperty("assetKey") String assetKey,
      @JsonProperty("location") String location,
      @JsonProperty("alt") String alt,
      @JsonProperty("displayOrder") Integer displayOrder) {}
}
