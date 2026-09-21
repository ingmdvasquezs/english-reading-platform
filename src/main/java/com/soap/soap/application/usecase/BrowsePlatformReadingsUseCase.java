package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.CollectionNotFoundException;
import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.UserNotFoundException;
import com.soap.soap.application.model.BrowsePlatformReadingsQuery;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.RecommendedPlatformReading;
import com.soap.soap.application.port.in.BrowsePlatformReadingsPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingCollectionRepositoryPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
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
public class BrowsePlatformReadingsUseCase implements BrowsePlatformReadingsPort {
  private final UserRepositoryPort users;
  private final ReadingCollectionRepositoryPort collections;
  private final ReadingRepositoryPort readings;
  private final ReadingProgressRepositoryPort progress;
  private final PlatformReadingPersonalizationService personalizer;
  private final CurrentUserPort currentUser;

  @Override
  @Transactional(readOnly = true)
  public PageResult<RecommendedPlatformReading> browsePlatformReadings(
      BrowsePlatformReadingsQuery query) {
    if (query == null || query.pageRequest() == null) {
      throw new InvalidApplicationArgumentException("Query and page request must not be null");
    }

    var userId = currentUser.requireUserId();
    if (!users.existsById(userId)) {
      throw new UserNotFoundException(userId);
    }

    // Validate collection if specified
    if (query.collectionKey() != null && !query.collectionKey().isBlank()) {
      collections
          .findActiveByKey(query.collectionKey())
          .orElseThrow(() -> new CollectionNotFoundException(query.collectionKey()));
    }

    // Load user and determine learning language
    var user = users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    if (user.learningLanguage() == null || user.learningLanguage().isBlank()) {
      throw new IllegalStateException(
          "User " + userId + " does not have an active learning language configured");
    }
    String learningLanguage = LanguageTag.of(user.learningLanguage()).value();

    if (query.countryCode() != null && !query.countryCode().isBlank()) {
      if (!query.countryCode().matches("^[A-Z]{2}$")) {
        throw new InvalidApplicationArgumentException(
            "Country code must be 2 uppercase ISO letters: " + query.countryCode());
      }
    }

    String categoryDisplayName = query.category() != null ? query.category().displayName() : null;

    var page =
        readings.browsePlatformReadings(
            query.collectionKey(),
            categoryDisplayName,
            query.editorialLevel(),
            query.countryCode(),
            query.discoveryTopic(),
            query.sort() != null
                ? query.sort()
                : com.soap.soap.domain.model.PlatformReadingSort.DEFAULT,
            learningLanguage,
            query.pageRequest());

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
