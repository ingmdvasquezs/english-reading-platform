package com.soap.soap.infrastructure.editorial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soap.soap.application.exception.EditorialCollectionImportException;
import com.soap.soap.domain.model.EditorialLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EditorialCollectionManifestParserTest {

  private final EditorialCollectionManifestParser parser = new EditorialCollectionManifestParser();

  @Test
  @DisplayName("Parses valid collection manifest JSON")
  void parsesValidCollectionManifest() {
    String json =
        """
        {
          "schemaVersion": 1,
          "key": "colombian-myths-legends",
          "title": "Mitos y leyendas de Colombia",
          "description": "Una coleccion de relatos tradicionales",
          "displayOrder": 1,
          "active": true,
          "coverKey": null,
          "readings": [
            {
              "adaptationGroupKey": "mohan-pasuncha",
              "language": "en",
              "editorialLevel": "B1",
              "displayOrder": 1
            },
            {
              "adaptationGroupKey": "madremonte-colombia",
              "language": "en",
              "level": "B2",
              "displayOrder": 2
            }
          ]
        }
        """;

    var command = parser.parseJson(json);

    assertThat(command.key()).isEqualTo("colombian-myths-legends");
    assertThat(command.title()).isEqualTo("Mitos y leyendas de Colombia");
    assertThat(command.description()).isEqualTo("Una coleccion de relatos tradicionales");
    assertThat(command.displayOrder()).isEqualTo(1);
    assertThat(command.active()).isTrue();
    assertThat(command.coverKey()).isNull();
    assertThat(command.readings()).hasSize(2);

    var r1 = command.readings().get(0);
    assertThat(r1.adaptationGroupKey()).isEqualTo("mohan-pasuncha");
    assertThat(r1.language()).isEqualTo("en");
    assertThat(r1.editorialLevel()).isEqualTo(EditorialLevel.B1);
    assertThat(r1.displayOrder()).isEqualTo(1);

    var r2 = command.readings().get(1);
    assertThat(r2.adaptationGroupKey()).isEqualTo("madremonte-colombia");
    assertThat(r2.language()).isEqualTo("en");
    assertThat(r2.editorialLevel()).isEqualTo(EditorialLevel.B2);
    assertThat(r2.displayOrder()).isEqualTo(2);
  }

  @Test
  @DisplayName("Fails on unsupported schemaVersion")
  void failsOnUnsupportedSchemaVersion() {
    String json =
        """
        {
          "schemaVersion": 99,
          "key": "myths",
          "title": "Myths",
          "description": "Desc",
          "readings": []
        }
        """;

    assertThatThrownBy(() -> parser.parseJson(json))
        .isInstanceOf(EditorialCollectionImportException.class)
        .hasMessageContaining("Unsupported schemaVersion: 99");
  }

  @Test
  @DisplayName("Fails on invalid editorialLevel")
  void failsOnInvalidEditorialLevel() {
    String json =
        """
        {
          "schemaVersion": 1,
          "key": "myths",
          "title": "Myths",
          "description": "Desc",
          "readings": [
            {
              "adaptationGroupKey": "group",
              "language": "en",
              "level": "INVALID_LEVEL",
              "displayOrder": 1
            }
          ]
        }
        """;

    assertThatThrownBy(() -> parser.parseJson(json))
        .isInstanceOf(EditorialCollectionImportException.class)
        .hasMessageContaining("Invalid editorialLevel: INVALID_LEVEL");
  }
}
