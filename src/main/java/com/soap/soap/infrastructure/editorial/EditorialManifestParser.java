package com.soap.soap.infrastructure.editorial;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soap.soap.application.command.EditorialOptionCommand;
import com.soap.soap.application.command.EditorialQuestionCommand;
import com.soap.soap.application.command.IngestEditorialReadingCommand;
import com.soap.soap.application.command.UpdatePublishedEditorialContentCommand;
import com.soap.soap.domain.model.AccessTier;
import com.soap.soap.domain.model.AdaptationKind;
import com.soap.soap.domain.model.EditorialContentType;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.EditorialRegion;
import com.soap.soap.domain.model.QuestionType;
import com.soap.soap.domain.model.RightsStatus;
import com.soap.soap.domain.model.SourceKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class EditorialManifestParser {

  public static final int SUPPORTED_SCHEMA_VERSION = 1;

  private final ObjectMapper objectMapper;

  public EditorialManifestParser() {
    this(new ObjectMapper());
  }

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  public EditorialManifestParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
  }

  public IngestEditorialReadingCommand parse(Path path) {
    if (path == null) {
      throw new EditorialManifestParseException("Manifest path must not be null");
    }
    if (!Files.exists(path)) {
      throw new EditorialManifestParseException("Manifest file does not exist: " + path);
    }
    if (!Files.isRegularFile(path)) {
      throw new EditorialManifestParseException("Manifest path is not a regular file: " + path);
    }
    if (!Files.isReadable(path)) {
      throw new EditorialManifestParseException("Manifest file is not readable: " + path);
    }

    try {
      String json = Files.readString(path);
      return parseJson(json);
    } catch (IOException e) {
      throw new EditorialManifestParseException("Failed to read manifest file: " + path, e);
    }
  }

  public UpdatePublishedEditorialContentCommand parseUpdateContentCommand(Path path) {
    if (path == null) {
      throw new EditorialManifestParseException("Manifest path must not be null");
    }
    if (!Files.exists(path)) {
      throw new EditorialManifestParseException("Manifest file does not exist: " + path);
    }
    if (!Files.isRegularFile(path)) {
      throw new EditorialManifestParseException("Manifest path is not a regular file: " + path);
    }
    if (!Files.isReadable(path)) {
      throw new EditorialManifestParseException("Manifest file is not readable: " + path);
    }

    try {
      String json = Files.readString(path);
      return parseUpdateContentCommandJson(json);
    } catch (IOException e) {
      throw new EditorialManifestParseException("Failed to read manifest file: " + path, e);
    }
  }

  public UpdatePublishedEditorialContentCommand parseUpdateContentCommandJson(String json) {
    if (json == null || json.isBlank()) {
      throw new EditorialManifestParseException("Manifest JSON must not be blank");
    }

    EditorialManifestDto dto;
    try {
      dto = objectMapper.readValue(json, EditorialManifestDto.class);
    } catch (Exception e) {
      throw new EditorialManifestParseException(
          "Failed to parse manifest JSON: " + e.getMessage(), e);
    }

    if (dto.schemaVersion() == null) {
      throw new EditorialManifestParseException("Missing required 'schemaVersion' in manifest");
    }
    if (dto.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
      throw new EditorialManifestParseException(
          "Unsupported schemaVersion: "
              + dto.schemaVersion()
              + ". Only schemaVersion "
              + SUPPORTED_SCHEMA_VERSION
              + " is supported");
    }

    var r = dto.reading();
    String content = r != null && r.content() != null ? r.content() : dto.content();
    String language = r != null && r.language() != null ? r.language() : dto.language();
    String levelStr =
        r != null && r.editorialLevel() != null ? r.editorialLevel() : dto.editorialLevel();
    String adaptationGroupKey =
        r != null && r.adaptationGroupKey() != null
            ? r.adaptationGroupKey()
            : dto.adaptationGroupKey();

    EditorialLevel editorialLevel = parseEnum(EditorialLevel.class, levelStr, "editorialLevel");

    List<EditorialQuestionCommand> questions =
        dto.comprehensionQuiz() == null ? null : parseQuestions(dto.comprehensionQuiz());

    return new UpdatePublishedEditorialContentCommand(
        adaptationGroupKey, language, editorialLevel, content, questions);
  }

  public IngestEditorialReadingCommand parseJson(String json) {
    if (json == null || json.isBlank()) {
      throw new EditorialManifestParseException("Manifest JSON must not be blank");
    }

    EditorialManifestDto dto;
    try {
      dto = objectMapper.readValue(json, EditorialManifestDto.class);
    } catch (Exception e) {
      throw new EditorialManifestParseException(
          "Failed to parse manifest JSON: " + e.getMessage(), e);
    }

    if (dto.schemaVersion() == null) {
      throw new EditorialManifestParseException("Missing required 'schemaVersion' in manifest");
    }
    if (dto.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
      throw new EditorialManifestParseException(
          "Unsupported schemaVersion: "
              + dto.schemaVersion()
              + ". Only schemaVersion "
              + SUPPORTED_SCHEMA_VERSION
              + " is supported");
    }

    // Resolve reading metadata from nested 'reading' object or top-level fields
    var r = dto.reading();
    String title = r != null && r.title() != null ? r.title() : dto.title();
    String content = r != null && r.content() != null ? r.content() : dto.content();
    String language = r != null && r.language() != null ? r.language() : dto.language();
    String levelStr =
        r != null && r.editorialLevel() != null ? r.editorialLevel() : dto.editorialLevel();
    String category = r != null && r.category() != null ? r.category() : dto.category();
    String shortDesc =
        r != null && r.shortDescription() != null ? r.shortDescription() : dto.shortDescription();
    String contentTypeStr =
        r != null && r.contentType() != null ? r.contentType() : dto.contentType();
    String countryCode = r != null && r.countryCode() != null ? r.countryCode() : dto.countryCode();
    String regionStr = r != null && r.region() != null ? r.region() : dto.region();
    String sourceKindStr = r != null && r.sourceKind() != null ? r.sourceKind() : dto.sourceKind();
    String rightsStatusStr =
        r != null && r.rightsStatus() != null ? r.rightsStatus() : dto.rightsStatus();
    String adaptationKindStr =
        r != null && r.adaptationKind() != null ? r.adaptationKind() : dto.adaptationKind();
    String sourceLanguage =
        r != null && r.sourceLanguage() != null ? r.sourceLanguage() : dto.sourceLanguage();
    String sourceTitle = r != null && r.sourceTitle() != null ? r.sourceTitle() : dto.sourceTitle();
    String sourceAuthor =
        r != null && r.sourceAuthor() != null ? r.sourceAuthor() : dto.sourceAuthor();
    String sourceUrl = r != null && r.sourceUrl() != null ? r.sourceUrl() : dto.sourceUrl();
    String sourceNotes = r != null && r.sourceNotes() != null ? r.sourceNotes() : dto.sourceNotes();
    String adaptationGroupKey =
        r != null && r.adaptationGroupKey() != null
            ? r.adaptationGroupKey()
            : dto.adaptationGroupKey();
    String coverKey = r != null && r.coverKey() != null ? r.coverKey() : dto.coverKey();
    String coverAttribution =
        r != null && r.coverAttribution() != null ? r.coverAttribution() : dto.coverAttribution();
    String accessTierStr = r != null && r.accessTier() != null ? r.accessTier() : dto.accessTier();

    EditorialLevel editorialLevel = parseEnum(EditorialLevel.class, levelStr, "editorialLevel");
    EditorialContentType contentType =
        parseEnum(EditorialContentType.class, contentTypeStr, "contentType");
    EditorialRegion region = parseEnum(EditorialRegion.class, regionStr, "region");
    SourceKind sourceKind = parseEnum(SourceKind.class, sourceKindStr, "sourceKind");
    RightsStatus rightsStatus = parseEnum(RightsStatus.class, rightsStatusStr, "rightsStatus");
    AdaptationKind adaptationKind =
        parseEnum(AdaptationKind.class, adaptationKindStr, "adaptationKind");
    AccessTier accessTier = parseEnum(AccessTier.class, accessTierStr, "accessTier");

    List<EditorialQuestionCommand> questions = parseQuestions(dto.comprehensionQuiz());

    return new IngestEditorialReadingCommand(
        title,
        content,
        language,
        editorialLevel,
        category,
        shortDesc,
        contentType,
        countryCode,
        region,
        sourceKind,
        rightsStatus,
        adaptationKind,
        sourceLanguage,
        sourceTitle,
        sourceAuthor,
        sourceUrl,
        sourceNotes,
        adaptationGroupKey,
        coverKey,
        coverAttribution,
        accessTier,
        questions);
  }

  private <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value, String fieldName) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Enum.valueOf(enumClass, value.trim());
    } catch (IllegalArgumentException e) {
      throw new EditorialManifestParseException(
          "Invalid value '" + value + "' for field '" + fieldName + "'");
    }
  }

  private List<EditorialQuestionCommand> parseQuestions(
      List<EditorialManifestDto.QuestionDto> quizDto) {
    if (quizDto == null) {
      return List.of();
    }
    var questions = new ArrayList<EditorialQuestionCommand>();
    for (var q : quizDto) {
      int ordinal = q.ordinal() != null ? q.ordinal() : 0;
      QuestionType type = parseEnum(QuestionType.class, q.questionType(), "questionType");
      var options = new ArrayList<EditorialOptionCommand>();
      if (q.options() != null) {
        for (var opt : q.options()) {
          options.add(
              new EditorialOptionCommand(
                  opt.ordinal() != null ? opt.ordinal() : 0,
                  opt.content(),
                  Boolean.TRUE.equals(opt.isCorrect())));
        }
      }
      questions.add(
          new EditorialQuestionCommand(ordinal, type, q.prompt(), q.explanation(), options));
    }
    return questions;
  }
}
