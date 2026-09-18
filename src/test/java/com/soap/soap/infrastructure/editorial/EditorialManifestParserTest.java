package com.soap.soap.infrastructure.editorial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soap.soap.domain.model.AccessTier;
import com.soap.soap.domain.model.AdaptationKind;
import com.soap.soap.domain.model.EditorialContentType;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.EditorialRegion;
import com.soap.soap.domain.model.QuestionType;
import com.soap.soap.domain.model.RightsStatus;
import com.soap.soap.domain.model.SourceKind;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditorialManifestParserTest {

  private EditorialManifestParser parser;

  @BeforeEach
  void setUp() {
    parser = new EditorialManifestParser(new ObjectMapper());
  }

  private String validJson() {
    return """
        {
          "schemaVersion": 1,
          "reading": {
            "title": "A Test Story",
            "content": "This is a completely fictitious narrative for test parsing.",
            "language": "en",
            "editorialLevel": "A2",
            "category": "Daily Life & Relationships",
            "shortDescription": "Fictional test story.",
            "contentType": "REAL_STORY",
            "countryCode": "US",
            "region": "NORTHERN_AMERICA",
            "sourceKind": "ORIGINAL_EDITORIAL",
            "rightsStatus": "ORIGINAL",
            "adaptationKind": "ORIGINAL",
            "sourceLanguage": null,
            "sourceTitle": null,
            "sourceAuthor": null,
            "sourceUrl": null,
            "sourceNotes": null,
            "adaptationGroupKey": "fictional-test-story",
            "coverKey": "test-story-cover",
            "coverAttribution": "Staff",
            "accessTier": "FREE"
          },
          "comprehensionQuiz": [
            {
              "ordinal": 1,
              "questionType": "FACTUAL",
              "prompt": "What is this test story about?",
              "explanation": "It is about testing.",
              "options": [
                { "ordinal": 1, "content": "Testing", "isCorrect": true },
                { "ordinal": 2, "content": "Cooking", "isCorrect": false },
                { "ordinal": 3, "content": "Flying", "isCorrect": false },
                { "ordinal": 4, "content": "Dancing", "isCorrect": false }
              ]
            }
          ]
        }
        """;
  }

  @Test
  void successfullyParsesValidManifestFile(@TempDir Path tempDir) throws Exception {
    var file = tempDir.resolve("test-manifest.json");
    Files.writeString(file, validJson());

    var command = parser.parse(file);

    assertThat(command.title()).isEqualTo("A Test Story");
    assertThat(command.language()).isEqualTo("en");
    assertThat(command.editorialLevel()).isEqualTo(EditorialLevel.A2);
    assertThat(command.category()).isEqualTo("Daily Life & Relationships");
    assertThat(command.contentType()).isEqualTo(EditorialContentType.REAL_STORY);
    assertThat(command.region()).isEqualTo(EditorialRegion.NORTHERN_AMERICA);
    assertThat(command.sourceKind()).isEqualTo(SourceKind.ORIGINAL_EDITORIAL);
    assertThat(command.rightsStatus()).isEqualTo(RightsStatus.ORIGINAL);
    assertThat(command.adaptationKind()).isEqualTo(AdaptationKind.ORIGINAL);
    assertThat(command.accessTier()).isEqualTo(AccessTier.FREE);
    assertThat(command.adaptationGroupKey()).isEqualTo("fictional-test-story");
    assertThat(command.coverKey()).isEqualTo("test-story-cover");
    assertThat(command.questions()).hasSize(1);
    assertThat(command.questions().getFirst().questionType()).isEqualTo(QuestionType.FACTUAL);
    assertThat(command.questions().getFirst().options()).hasSize(4);
    assertThat(command.questions().getFirst().options().getFirst().isCorrect()).isTrue();
  }

  @Test
  void rejectsMissingSchemaVersion() {
    String json =
        """
        {
          "reading": {
            "title": "Story"
          }
        }
        """;

    assertThatThrownBy(() -> parser.parseJson(json))
        .isInstanceOf(EditorialManifestParseException.class)
        .hasMessageContaining("Missing required 'schemaVersion'");
  }

  @Test
  void rejectsUnsupportedSchemaVersion() {
    String json =
        """
        {
          "schemaVersion": 2,
          "reading": {
            "title": "Story"
          }
        }
        """;

    assertThatThrownBy(() -> parser.parseJson(json))
        .isInstanceOf(EditorialManifestParseException.class)
        .hasMessageContaining("Unsupported schemaVersion: 2");
  }

  @Test
  void rejectsNonExistentFile(@TempDir Path tempDir) {
    var nonExistent = tempDir.resolve("does-not-exist.json");
    assertThatThrownBy(() -> parser.parse(nonExistent))
        .isInstanceOf(EditorialManifestParseException.class)
        .hasMessageContaining("does not exist");
  }

  @Test
  void rejectsMalformedJson() {
    String malformed = "{ schemaVersion: 1, unclosed ...";
    assertThatThrownBy(() -> parser.parseJson(malformed))
        .isInstanceOf(EditorialManifestParseException.class)
        .hasMessageContaining("Failed to parse manifest JSON");
  }

  @Test
  void successfullyParsesUpdateContentCommand() {
    var cmd = parser.parseUpdateContentCommandJson(validJson());
    assertThat(cmd.adaptationGroupKey()).isEqualTo("fictional-test-story");
    assertThat(cmd.language()).isEqualTo("en");
    assertThat(cmd.editorialLevel()).isEqualTo(EditorialLevel.A2);
    assertThat(cmd.content())
        .isEqualTo("This is a completely fictitious narrative for test parsing.");
    assertThat(cmd.questions()).hasSize(1);
  }

  @Test
  void successfullyParsesUpdateContentCommandWithNullQuiz() {
    String jsonWithoutQuiz =
        """
        {
          "schemaVersion": 1,
          "reading": {
            "adaptationGroupKey": "story-key",
            "language": "en",
            "editorialLevel": "B1",
            "content": "Story content."
          }
        }
        """;
    var cmd = parser.parseUpdateContentCommandJson(jsonWithoutQuiz);
    assertThat(cmd.adaptationGroupKey()).isEqualTo("story-key");
    assertThat(cmd.questions()).isNull();
  }

  @Test
  void successfullyParsesManifestWithDiscoveryTopic(@TempDir Path tempDir) throws Exception {
    String json =
        """
        {
          "schemaVersion": 1,
          "reading": {
            "title": "The Mohan",
            "content": "A legend from Tolima.",
            "language": "en",
            "editorialLevel": "B1",
            "category": "Culture, Arts & Fiction",
            "shortDescription": "Legend of Mohan",
            "contentType": "MYTH",
            "countryCode": "CO",
            "region": "SOUTH_AMERICA",
            "sourceKind": "ORIGINAL_EDITORIAL",
            "rightsStatus": "ORIGINAL",
            "adaptationKind": "ORIGINAL",
            "adaptationGroupKey": "the-mohan",
            "coverKey": "hero-colombia-el-mohan",
            "accessTier": "FREE",
            "discoveryTopic": "MYTHS_AND_LEGENDS"
          },
          "comprehensionQuiz": []
        }
        """;
    var file = tempDir.resolve("mohan-manifest.json");
    Files.writeString(file, json);

    var command = parser.parse(file);

    assertThat(command.discoveryTopic())
        .isEqualTo(com.soap.soap.domain.model.DiscoveryTopic.MYTHS_AND_LEGENDS);
    assertThat(command.countryCode()).isEqualTo("CO");
  }
}
