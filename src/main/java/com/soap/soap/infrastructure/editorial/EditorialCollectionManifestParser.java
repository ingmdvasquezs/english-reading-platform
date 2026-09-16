package com.soap.soap.infrastructure.editorial;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soap.soap.application.command.EditorialCollectionReadingItemCommand;
import com.soap.soap.application.command.ImportEditorialCollectionCommand;
import com.soap.soap.application.exception.EditorialCollectionImportException;
import com.soap.soap.domain.model.EditorialLevel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class EditorialCollectionManifestParser {

  public static final int SUPPORTED_SCHEMA_VERSION = 1;

  private final ObjectMapper objectMapper;

  public EditorialCollectionManifestParser() {
    this(new ObjectMapper());
  }

  @org.springframework.beans.factory.annotation.Autowired(required = false)
  public EditorialCollectionManifestParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
  }

  public ImportEditorialCollectionCommand parse(Path path) {
    if (path == null) {
      throw new EditorialCollectionImportException("Manifest path must not be null");
    }
    if (!Files.exists(path)) {
      throw new EditorialCollectionImportException("Manifest file does not exist: " + path);
    }
    if (!Files.isRegularFile(path)) {
      throw new EditorialCollectionImportException("Manifest path is not a regular file: " + path);
    }
    if (!Files.isReadable(path)) {
      throw new EditorialCollectionImportException("Manifest file is not readable: " + path);
    }

    try {
      String json = Files.readString(path);
      return parseJson(json);
    } catch (IOException e) {
      throw new EditorialCollectionImportException("Failed to read manifest file: " + path, e);
    }
  }

  public ImportEditorialCollectionCommand parseJson(String json) {
    if (json == null || json.isBlank()) {
      throw new EditorialCollectionImportException("Manifest JSON must not be blank");
    }

    if (json.startsWith("\uFEFF")) {
      json = json.substring(1);
    }

    try {
      JsonNode root = objectMapper.readTree(json);

      if (!root.hasNonNull("schemaVersion")) {
        throw new EditorialCollectionImportException("Missing required field: schemaVersion");
      }
      int schemaVersion = root.get("schemaVersion").asInt();
      if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
        throw new EditorialCollectionImportException(
            "Unsupported schemaVersion: "
                + schemaVersion
                + ". Expected: "
                + SUPPORTED_SCHEMA_VERSION);
      }

      String key = textOrNull(root, "key");
      String title = textOrNull(root, "title");
      if (title == null) {
        title = textOrNull(root, "displayName");
      }
      String description = textOrNull(root, "description");
      int displayOrder = root.has("displayOrder") ? root.get("displayOrder").asInt(1) : 1;
      boolean active = !root.has("active") || root.get("active").asBoolean(true);
      String coverKey = textOrNull(root, "coverKey");

      JsonNode readingsNode = root.get("readings");
      if (readingsNode == null || !readingsNode.isArray()) {
        throw new EditorialCollectionImportException(
            "Missing or non-array required field: readings");
      }

      List<EditorialCollectionReadingItemCommand> readingCommands = new ArrayList<>();
      for (int i = 0; i < readingsNode.size(); i++) {
        JsonNode node = readingsNode.get(i);
        String groupKey = textOrNull(node, "adaptationGroupKey");
        String language = textOrNull(node, "language");

        String levelStr = textOrNull(node, "editorialLevel");
        if (levelStr == null) {
          levelStr = textOrNull(node, "level");
        }
        EditorialLevel level = null;
        if (levelStr != null && !levelStr.isBlank()) {
          try {
            level = EditorialLevel.valueOf(levelStr.trim().toUpperCase());
          } catch (IllegalArgumentException e) {
            throw new EditorialCollectionImportException(
                "Invalid editorialLevel: " + levelStr + " at index " + i);
          }
        }

        int itemOrder = node.has("displayOrder") ? node.get("displayOrder").asInt(0) : 0;
        readingCommands.add(
            new EditorialCollectionReadingItemCommand(groupKey, language, level, itemOrder));
      }

      return new ImportEditorialCollectionCommand(
          key, title, description, displayOrder, active, coverKey, readingCommands);
    } catch (EditorialCollectionImportException e) {
      throw e;
    } catch (Exception e) {
      throw new EditorialCollectionImportException("Failed to parse collection manifest JSON", e);
    }
  }

  private String textOrNull(JsonNode node, String fieldName) {
    if (node.hasNonNull(fieldName)) {
      String val = node.get(fieldName).asText();
      return val != null ? val.trim() : null;
    }
    return null;
  }
}
