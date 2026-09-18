package com.soap.soap.infrastructure.editorial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiscoveryManifestParserTest {

  private DiscoveryManifestParser parser;

  @BeforeEach
  void setUp() {
    parser = new DiscoveryManifestParser(new ObjectMapper());
  }

  @Test
  void successfullyParsesValidRegionManifest(@TempDir Path tempDir) throws Exception {
    String json =
        """
        {
          "schemaVersion": 1,
          "key": "latin-america",
          "displayName": "Latinoamérica",
          "subtitle": "Explora relatos, mitos e historias fascinantes de América Latina.",
          "displayOrder": 1,
          "active": true
        }
        """;
    var file = tempDir.resolve("region.json");
    Files.writeString(file, json);

    var region = parser.parseRegion(file);

    assertThat(region.key()).isEqualTo("latin-america");
    assertThat(region.displayName()).isEqualTo("Latinoamérica");
    assertThat(region.subtitle())
        .isEqualTo("Explora relatos, mitos e historias fascinantes de América Latina.");
    assertThat(region.displayOrder()).isEqualTo(1);
    assertThat(region.active()).isTrue();
  }

  @Test
  void successfullyParsesValidCountryManifest(@TempDir Path tempDir) throws Exception {
    String json =
        """
        {
          "schemaVersion": 1,
          "regionKey": "latin-america",
          "countryCode": "CO",
          "displayName": "Colombia",
          "tagline": "Historias, lugares, mitos y tradiciones.",
          "description": "Tierra de mitos y biodiversidad.",
          "displayOrder": 1,
          "active": true,
          "heroImages": [
            {
              "assetKey": "editorial/heroes/colombia/hero-colombia-villa-de-leyva.webp",
              "location": "Villa de Leyva, Boyacá",
              "alt": "Villa de Leyva",
              "displayOrder": 1
            },
            {
              "assetKey": "editorial/heroes/colombia/hero-colombia-valle-de-cocora.webp",
              "location": "Valle de Cocora, Quindío",
              "alt": "Valle de Cocora",
              "displayOrder": 2
            }
          ]
        }
        """;
    var file = tempDir.resolve("country.json");
    Files.writeString(file, json);

    var country = parser.parseCountry(file);

    assertThat(country.countryCode()).isEqualTo("CO");
    assertThat(country.displayName()).isEqualTo("Colombia");
    assertThat(country.tagline()).isEqualTo("Historias, lugares, mitos y tradiciones.");
    assertThat(country.description()).isEqualTo("Tierra de mitos y biodiversidad.");
    assertThat(country.heroImages()).hasSize(2);
    assertThat(country.heroImages().getFirst().assetKey())
        .isEqualTo("editorial/heroes/colombia/hero-colombia-villa-de-leyva.webp");
    assertThat(country.heroImages().getFirst().location()).isEqualTo("Villa de Leyva, Boyacá");
    assertThat(country.heroImages().getFirst().alt()).isEqualTo("Villa de Leyva");
  }

  @Test
  void rejectsMissingSchemaVersionInRegion(@TempDir Path tempDir) throws Exception {
    String json =
        """
        {
          "key": "latin-america",
          "displayName": "Latinoamérica"
        }
        """;
    var file = tempDir.resolve("region.json");
    Files.writeString(file, json);

    assertThatThrownBy(() -> parser.parseRegion(file))
        .isInstanceOf(EditorialManifestParseException.class)
        .hasMessageContaining("Unsupported schemaVersion: null");
  }

  @Test
  void rejectsUnsupportedSchemaVersionInCountry(@TempDir Path tempDir) throws Exception {
    String json =
        """
        {
          "schemaVersion": 99,
          "countryCode": "CO"
        }
        """;
    var file = tempDir.resolve("country.json");
    Files.writeString(file, json);

    assertThatThrownBy(() -> parser.parseCountry(file))
        .isInstanceOf(EditorialManifestParseException.class)
        .hasMessageContaining("Unsupported schemaVersion: 99");
  }
}
