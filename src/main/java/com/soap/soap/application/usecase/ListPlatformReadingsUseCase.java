package com.soap.soap.application.usecase;

import com.soap.soap.application.exception.InvalidApplicationArgumentException;
import com.soap.soap.application.exception.UserNotFoundException;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.PlatformReadingSummary;
import com.soap.soap.application.port.in.ListPlatformReadingsPort;
import com.soap.soap.application.port.out.CurrentUserPort;
import com.soap.soap.application.port.out.ReadingProgressRepositoryPort;
import com.soap.soap.application.port.out.ReadingRepositoryPort;
import com.soap.soap.application.port.out.UserRepositoryPort;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ListPlatformReadingsUseCase implements ListPlatformReadingsPort {
  private final UserRepositoryPort users;
  private final ReadingRepositoryPort readings;
  private final ReadingProgressRepositoryPort progress;
  private final CurrentUserPort currentUser;

  @Override
  @Transactional(readOnly = true)
  public PageResult<PlatformReadingSummary> listPlatformReadings(PageRequest pageRequest) {
    var userId = currentUser.requireUserId();
    if (pageRequest == null) {
      throw new InvalidApplicationArgumentException("Page request must not be null");
    }
    if (!users.existsById(userId)) {
      throw new UserNotFoundException(userId);
    }
    var page = readings.findPlatformSummaries(pageRequest);
    var progressByReading =
        progress.findByUserIdAndReadingIds(
            userId,
            page.content().stream().map(PlatformReadingSummary::id).collect(Collectors.toSet()));
    var summaries =
        page.content().stream()
            .map(
                summary ->
                    new PlatformReadingSummary(
                        summary.id(),
                        summary.title(),
                        summary.language(),
                        summary.editorialLevel(),
                        summary.category(),
                        summary.createdAt(),
                        progressByReading.containsKey(summary.id())
                            ? progressByReading.get(summary.id()).status()
                            : null,
                        summary.coverKey()))
            .toList();
    return new PageResult<>(summaries, page.page(), page.size(), page.totalElements());
  }
}
