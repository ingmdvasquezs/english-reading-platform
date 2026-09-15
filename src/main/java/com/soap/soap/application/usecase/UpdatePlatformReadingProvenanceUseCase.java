package com.soap.soap.application.usecase;

import com.soap.soap.application.command.UpdatePlatformReadingProvenanceCommand;
import com.soap.soap.application.exception.EditorialProvenanceUpdateException;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.ReadingNotFoundException;
import com.soap.soap.application.port.in.UpdatePlatformReadingProvenancePort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.domain.model.LanguageTag;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingOrigin;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UpdatePlatformReadingProvenanceUseCase implements UpdatePlatformReadingProvenancePort {

  private final ReadingRepositoryPort readings;

  @Override
  @Transactional
  public Reading updateProvenance(UpdatePlatformReadingProvenanceCommand command) {
    if (command == null) {
      throw new InvalidApplicationArgumentException("Provenance update command must not be null");
    }

    Reading reading;
    if (command.readingId() != null) {
      reading =
          readings
              .findById(command.readingId())
              .orElseThrow(() -> new ReadingNotFoundException(command.readingId()));
    } else {
      if (command.adaptationGroupKey() == null || command.adaptationGroupKey().isBlank()) {
        throw new InvalidApplicationArgumentException("Adaptation group key must not be blank");
      }
      if (command.language() == null || command.language().isBlank()) {
        throw new InvalidApplicationArgumentException("Language must not be blank");
      }
      if (command.editorialLevel() == null) {
        throw new InvalidApplicationArgumentException("Editorial level must not be null");
      }

      String normalizedLanguage = LanguageTag.of(command.language()).value();
      String normalizedGroupKey = command.adaptationGroupKey().trim();

      reading =
          readings
              .findPlatformReadingByAdaptationKey(
                  normalizedGroupKey, normalizedLanguage, command.editorialLevel())
              .orElseThrow(
                  () ->
                      new EditorialProvenanceUpdateException(
                          "Platform reading not found for key: "
                              + normalizedGroupKey
                              + ", language: "
                              + normalizedLanguage
                              + ", level: "
                              + command.editorialLevel()));
    }

    if (reading.origin() != ReadingOrigin.PLATFORM) {
      throw new EditorialProvenanceUpdateException(
          "Only PLATFORM readings can have provenance updated, found origin: " + reading.origin());
    }

    var updatedReading =
        new Reading(
            reading.id(),
            reading.user(),
            reading.title(),
            reading.content(),
            reading.language(),
            reading.createdAt(),
            reading.origin(),
            reading.editorialLevel(),
            reading.category(),
            reading.coverKey(),
            reading.editorialStatus(),
            reading.shortDescription(),
            reading.contentType(),
            reading.countryCode(),
            reading.region(),
            reading.sourceKind(),
            reading.rightsStatus(),
            reading.adaptationKind(),
            reading.sourceLanguage(),
            command.sourceTitle(),
            command.sourceAuthor(),
            command.sourceUrl(),
            command.sourceNotes(),
            reading.adaptationGroupKey(),
            reading.coverAttribution(),
            reading.accessTier());

    return readings.save(updatedReading);
  }
}
