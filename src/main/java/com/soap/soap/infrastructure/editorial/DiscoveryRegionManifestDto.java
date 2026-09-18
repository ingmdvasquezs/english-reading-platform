package com.soap.soap.infrastructure.editorial;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DiscoveryRegionManifestDto(
    @JsonProperty("schemaVersion") Integer schemaVersion,
    @JsonProperty("key") String key,
    @JsonProperty("displayName") String displayName,
    @JsonProperty("subtitle") String subtitle,
    @JsonProperty("displayOrder") Integer displayOrder,
    @JsonProperty("active") Boolean active) {}
