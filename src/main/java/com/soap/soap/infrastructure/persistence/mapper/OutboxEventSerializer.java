package com.soap.soap.infrastructure.persistence.mapper;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.soap.soap.domain.model.DocumentImportRequestedEvent;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OutboxEventSerializer {

  private final ObjectMapper objectMapper;

  public OutboxEventSerializer() {
    this(new ObjectMapper());
  }

  @Autowired(required = false)
  public OutboxEventSerializer(ObjectMapper objectMapper) {
    ObjectMapper mapper = objectMapper != null ? objectMapper.copy() : new ObjectMapper();
    SimpleModule timeModule = new SimpleModule();
    timeModule.addSerializer(
        LocalDateTime.class,
        new JsonSerializer<>() {
          @Override
          public void serialize(
              LocalDateTime value, JsonGenerator gen, SerializerProvider serializers)
              throws IOException {
            gen.writeString(
                value != null ? value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
          }
        });
    timeModule.addDeserializer(
        LocalDateTime.class,
        new JsonDeserializer<>() {
          @Override
          public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt)
              throws IOException {
            String text = p.getText();
            return text != null && !text.isBlank()
                ? LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : null;
          }
        });
    mapper.registerModule(timeModule);
    this.objectMapper = mapper;
  }

  public String serializeDocumentImportRequested(DocumentImportRequestedEvent event) {
    try {
      return objectMapper.writeValueAsString(event);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Failed to serialize DocumentImportRequestedEvent", e);
    }
  }

  public DocumentImportRequestedEvent deserializeDocumentImportRequested(String payload) {
    try {
      return objectMapper.readValue(payload, DocumentImportRequestedEvent.class);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Failed to deserialize DocumentImportRequestedEvent", e);
    }
  }
}
