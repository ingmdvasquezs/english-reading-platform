package com.soap.soap.application.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DocumentImportQueueMessageTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("H: JSON envelope contains correct contract identifiers and version")
  void jsonEnvelopeContract() throws Exception {
    UUID eventId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID importJobId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    DocumentImportQueueMessage message =
        DocumentImportQueueMessage.forImportJob(eventId, importJobId);

    String json = objectMapper.writeValueAsString(message);
    JsonNode root = objectMapper.readTree(json);

    assertThat(root.has("schemaVersion")).isTrue();
    assertThat(root.get("schemaVersion").asInt()).isEqualTo(1);

    assertThat(root.has("eventId")).isTrue();
    assertThat(root.get("eventId").asText()).isEqualTo("11111111-1111-1111-1111-111111111111");

    assertThat(root.has("eventType")).isTrue();
    assertThat(root.get("eventType").asText()).isEqualTo("DOCUMENT_IMPORT_REQUESTED");

    assertThat(root.has("importJobId")).isTrue();
    assertThat(root.get("importJobId").asText()).isEqualTo("22222222-2222-2222-2222-222222222222");

    // Round-trip deserialization
    DocumentImportQueueMessage deserialized =
        objectMapper.readValue(json, DocumentImportQueueMessage.class);
    assertThat(deserialized.schemaVersion()).isEqualTo(1);
    assertThat(deserialized.eventId()).isEqualTo(eventId);
    assertThat(deserialized.eventType()).isEqualTo("DOCUMENT_IMPORT_REQUESTED");
    assertThat(deserialized.importJobId()).isEqualTo(importJobId);
  }

  @Test
  @DisplayName("Validates invariants: schemaVersion must be positive, no null or blank fields")
  void validatesInvariants() {
    UUID eventId = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();

    assertThatThrownBy(() -> new DocumentImportQueueMessage(0, eventId, "TYPE", jobId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("schemaVersion must be positive");

    assertThatThrownBy(() -> new DocumentImportQueueMessage(-1, eventId, "TYPE", jobId))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> new DocumentImportQueueMessage(1, null, "TYPE", jobId))
        .isInstanceOf(NullPointerException.class);

    assertThatThrownBy(() -> new DocumentImportQueueMessage(1, eventId, null, jobId))
        .isInstanceOf(NullPointerException.class);

    assertThatThrownBy(() -> new DocumentImportQueueMessage(1, eventId, "   ", jobId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("eventType must not be blank");

    assertThatThrownBy(() -> new DocumentImportQueueMessage(1, eventId, "TYPE", null))
        .isInstanceOf(NullPointerException.class);
  }
}
