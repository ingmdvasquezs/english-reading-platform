package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.CollectionNotFoundException;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.UserNotFoundException;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.RecommendedPlatformReading;
import com.soap.soap.application.port.in.ListCollectionReadingsPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingCollectionRepositoryPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import com.soap.soap.application.service.PlatformReadingPersonalizationService;
import com.soap.soap.domain.model.LanguageTag;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingProgressStatus;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ListCollectionReadingsUseCase implements ListCollectionReadingsPort {
  private final UserRepositoryPort users;
  private final ReadingCollectionRepositoryPort collections;
  private final ReadingProgressRepositoryPort progress;
  private final PlatformReadingPersonalizationService personalizer;
  private final CurrentUserPort currentUser;

  @Override
  @Transactional(readOnly = true)
  public PageResult<RecommendedPlatformReading> listCollectionReadings(
      String collectionKey, PageRequest pageRequest) {
    var userId = currentUser.requireUserId();
    if (collectionKey == null || collectionKey.isBlank()) {
      throw new InvalidApplicationArgumentException("Collection key must not be blank");
    }
    if (pageRequest == null) {
      throw new InvalidApplicationArgumentException("Page request must not be null");
    }
    if (!users.existsById(userId)) {
      throw new UserNotFoundException(userId);
    }
    // Verify collection exists
    collections
        .findActiveByKey(collectionKey)
        .orElseThrow(() -> new CollectionNotFoundException(collectionKey));

    // Load user and determine learning language
    var user = users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    if (user.learningLanguage() == null || user.learningLanguage().isBlank()) {
      throw new IllegalStateException(
          "User " + userId + " does not have an active learning language configured");
    }
    String learningLanguage = LanguageTag.of(user.learningLanguage()).value();

    // Fetch readings scoped to user language
    var page = collections.findReadings(collectionKey, learningLanguage, pageRequest);
    if (page.content().isEmpty()) {
      return new PageResult<>(List.of(), page.page(), page.size(), page.totalElements());
    }

    var progressByReading =
        progress.findByUserIdAndReadingIds(
            userId, page.content().stream().map(Reading::id).collect(Collectors.toSet()));

    Map<UUID, ReadingProgressStatus> progressStatusByReading = new HashMap<>();
    progressByReading.forEach((id, p) -> progressStatusByReading.put(id, p.status()));

    var summaries =
        personalizer.personalizeReadings(
            userId, learningLanguage, page.content(), progressStatusByReading);

    return new PageResult<>(summaries, page.page(), page.size(), page.totalElements());
  }
}
