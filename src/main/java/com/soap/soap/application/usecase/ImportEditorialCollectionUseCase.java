package com.soap.soap.application.usecase;

import com.soap.soap.application.command.ImportEditorialCollectionCommand;
import com.soap.soap.application.exception.EditorialCollectionImportException;
import com.soap.soap.application.model.CollectionMembershipItem;
import com.soap.soap.application.model.ImportEditorialCollectionResult;
import com.soap.soap.application.port.in.ImportEditorialCollectionPort;
import com.soap.soap.application.port.out.ReadingCollectionRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.domain.model.EditorialStatus;
import com.soap.soap.domain.model.LanguageTag;
import com.soap.soap.domain.model.ReadingCollection;
import com.soap.soap.domain.model.ReadingOrigin;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImportEditorialCollectionUseCase implements ImportEditorialCollectionPort {

  private final ReadingCollectionRepositoryPort collections;
  private final ReadingRepositoryPort readings;

  @Override
  @Transactional
  public ImportEditorialCollectionResult importCollection(
      ImportEditorialCollectionCommand command) {
    validateCommand(command);

    // Pass 1: Validate command items and check for duplicates upfront
    Set<String> seenIdentities = new HashSet<>();
    Set<Integer> seenOrders = new HashSet<>();

    for (var item : command.readings()) {
      if (item == null) {
        throw new EditorialCollectionImportException("Reading item in collection must not be null");
      }
      if (item.adaptationGroupKey() == null || item.adaptationGroupKey().isBlank()) {
        throw new EditorialCollectionImportException(
            "Reading adaptationGroupKey must not be blank");
      }
      if (item.language() == null || item.language().isBlank()) {
        throw new EditorialCollectionImportException("Reading language must not be blank");
      }
      if (item.editorialLevel() == null) {
        throw new EditorialCollectionImportException("Reading editorialLevel must not be null");
      }
      if (item.displayOrder() <= 0) {
        throw new EditorialCollectionImportException(
            "Reading membership displayOrder must be greater than 0, got: " + item.displayOrder());
      }

      String identityKey =
          item.adaptationGroupKey().trim()
              + ":"
              + item.language().trim().toLowerCase()
              + ":"
              + item.editorialLevel().name();
      if (!seenIdentities.add(identityKey)) {
        throw new EditorialCollectionImportException(
            "Duplicate reading identity in collection manifest: " + identityKey);
      }

      if (!seenOrders.add(item.displayOrder())) {
        throw new EditorialCollectionImportException(
            "Duplicate reading displayOrder in collection manifest: " + item.displayOrder());
      }
    }

    // Pass 2: Resolve readings and check status
    List<CollectionMembershipItem> membershipItems = new ArrayList<>();
    for (var item : command.readings()) {
      var langTag = LanguageTag.of(item.language());
      var readingOpt =
          readings.findPlatformReadingByAdaptationKey(
              item.adaptationGroupKey().trim(), langTag.value(), item.editorialLevel());

      if (readingOpt.isEmpty()) {
        throw new EditorialCollectionImportException(
            "Platform reading not found for adaptationGroupKey="
                + item.adaptationGroupKey()
                + ", language="
                + item.language()
                + ", editorialLevel="
                + item.editorialLevel());
      }

      var reading = readingOpt.get();
      if (reading.origin() != ReadingOrigin.PLATFORM) {
        throw new EditorialCollectionImportException(
            "Reading " + reading.id() + " is not of origin PLATFORM");
      }
      if (reading.editorialStatus() != EditorialStatus.PUBLISHED) {
        throw new EditorialCollectionImportException(
            "Reading "
                + reading.id()
                + " ("
                + reading.title()
                + ") is not PUBLISHED (status="
                + reading.editorialStatus()
                + ")");
      }

      membershipItems.add(new CollectionMembershipItem(reading.id(), item.displayOrder()));
    }

    var existingOpt = collections.findByKey(command.key().trim());
    UUID targetId;
    boolean created;

    if (existingOpt.isPresent()) {
      targetId = existingOpt.get().id();
      created = false;
      log.info("Updating existing collection: key={}, collectionId={}", command.key(), targetId);
    } else {
      targetId = UUID.randomUUID();
      created = true;
      log.info("Creating new collection: key={}, collectionId={}", command.key(), targetId);
    }

    var collection =
        new ReadingCollection(
            targetId,
            command.key().trim(),
            command.title().trim(),
            command.description().trim(),
            command.displayOrder(),
            command.active(),
            command.coverKey() == null || command.coverKey().isBlank()
                ? null
                : command.coverKey().trim());

    collections.save(collection);
    collections.replaceMemberships(targetId, membershipItems);

    log.info(
        "Collection imported successfully: collectionId={}, key={}, created={}, memberships={}",
        targetId,
        command.key(),
        created,
        membershipItems.size());

    return new ImportEditorialCollectionResult(
        targetId, command.key().trim(), command.title().trim(), created, membershipItems.size());
  }

  private void validateCommand(ImportEditorialCollectionCommand command) {
    if (command == null) {
      throw new EditorialCollectionImportException("Import collection command must not be null");
    }
    if (command.key() == null || command.key().isBlank()) {
      throw new EditorialCollectionImportException("Collection key must not be blank");
    }
    if (command.key().length() > 100) {
      throw new EditorialCollectionImportException(
          "Collection key exceeds max length of 100 characters");
    }
    if (command.title() == null || command.title().isBlank()) {
      throw new EditorialCollectionImportException("Collection title must not be blank");
    }
    if (command.title().length() > 150) {
      throw new EditorialCollectionImportException(
          "Collection title exceeds max length of 150 characters");
    }
    if (command.description() == null || command.description().isBlank()) {
      throw new EditorialCollectionImportException("Collection description must not be blank");
    }
    if (command.description().length() > 500) {
      throw new EditorialCollectionImportException(
          "Collection description exceeds max length of 500 characters");
    }
    if (command.displayOrder() <= 0) {
      throw new EditorialCollectionImportException(
          "Collection displayOrder must be greater than 0, got: " + command.displayOrder());
    }
    if (command.readings() == null || command.readings().isEmpty()) {
      throw new EditorialCollectionImportException(
          "Collection manifest must specify at least one reading");
    }
  }
}
