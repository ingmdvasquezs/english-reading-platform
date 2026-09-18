package com.soap.soap.infrastructure.editorial;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soap.soap.domain.model.DiscoveryCountry;
import com.soap.soap.domain.model.DiscoveryHeroImage;
import com.soap.soap.domain.model.DiscoveryRegion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DiscoveryManifestParser {

  public static final int SUPPORTED_SCHEMA_VERSION = 1;
  private final ObjectMapper objectMapper;

  public DiscoveryManifestParser() {
    this(new ObjectMapper());
  }

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  public DiscoveryManifestParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
  }

  public DiscoveryRegion parseRegion(Path path) {
    if (path == null) {
      throw new EditorialManifestParseException("Manifest path must not be null");
    }
    if (!Files.exists(path) || !Files.isRegularFile(path)) {
      throw new EditorialManifestParseException("Region manifest file does not exist: " + path);
    }
    try {
      String json = Files.readString(path);
      return parseRegionJson(json);
    } catch (IOException e) {
      throw new EditorialManifestParseException("Failed to read region manifest: " + path, e);
    }
  }

  public DiscoveryRegion parseRegionJson(String json) {
    if (json == null || json.isBlank()) {
      throw new EditorialManifestParseException("Region manifest JSON must not be blank");
    }
    DiscoveryRegionManifestDto dto;
    try {
      dto = objectMapper.readValue(json, DiscoveryRegionManifestDto.class);
    } catch (Exception e) {
      throw new EditorialManifestParseException(
          "Failed to parse region manifest: " + e.getMessage(), e);
    }
    if (dto.schemaVersion() == null || dto.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
      throw new EditorialManifestParseException(
          "Unsupported schemaVersion: " + dto.schemaVersion());
    }
    if (dto.key() == null || dto.key().isBlank()) {
      throw new EditorialManifestParseException("Region key must not be blank");
    }
    if (dto.displayName() == null || dto.displayName().isBlank()) {
      throw new EditorialManifestParseException("Region displayName must not be blank");
    }
    return new DiscoveryRegion(
        null,
        dto.key().trim(),
        dto.displayName().trim(),
        dto.subtitle() != null ? dto.subtitle().trim() : null,
        dto.displayOrder() != null ? dto.displayOrder() : 0,
        dto.active() != null ? dto.active() : true);
  }

  public DiscoveryCountry parseCountry(Path path) {
    if (path == null) {
      throw new EditorialManifestParseException("Manifest path must not be null");
    }
    if (!Files.exists(path) || !Files.isRegularFile(path)) {
      throw new EditorialManifestParseException("Country manifest file does not exist: " + path);
    }
    try {
      String json = Files.readString(path);
      return parseCountryJson(json);
    } catch (IOException e) {
      throw new EditorialManifestParseException("Failed to read country manifest: " + path, e);
    }
  }

  public DiscoveryCountry parseCountryJson(String json) {
    if (json == null || json.isBlank()) {
      throw new EditorialManifestParseException("Country manifest JSON must not be blank");
    }
    DiscoveryCountryManifestDto dto;
    try {
      dto = objectMapper.readValue(json, DiscoveryCountryManifestDto.class);
    } catch (Exception e) {
      throw new EditorialManifestParseException(
          "Failed to parse country manifest: " + e.getMessage(), e);
    }
    if (dto.schemaVersion() == null || dto.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
      throw new EditorialManifestParseException(
          "Unsupported schemaVersion: " + dto.schemaVersion());
    }
    if (dto.countryCode() == null || !dto.countryCode().trim().matches("^[A-Z]{2}$")) {
      throw new EditorialManifestParseException(
          "Country code must be 2 uppercase ISO letters: " + dto.countryCode());
    }
    if (dto.displayName() == null || dto.displayName().isBlank()) {
      throw new EditorialManifestParseException("Country displayName must not be blank");
    }

    List<DiscoveryHeroImage> images =
        dto.heroImages() == null
            ? List.of()
            : dto.heroImages().stream()
                .map(
                    img ->
                        new DiscoveryHeroImage(
                            null,
                            img.assetKey(),
                            img.location(),
                            img.alt(),
                            img.displayOrder() != null ? img.displayOrder() : 0))
                .toList();

    return new DiscoveryCountry(
        null,
        dto.countryCode().trim().toUpperCase(),
        null,
        dto.displayName().trim(),
        dto.tagline() != null ? dto.tagline().trim() : "",
        dto.description() != null ? dto.description().trim() : "",
        dto.displayOrder() != null ? dto.displayOrder() : 0,
        dto.active() != null ? dto.active() : true,
        images);
  }
}
