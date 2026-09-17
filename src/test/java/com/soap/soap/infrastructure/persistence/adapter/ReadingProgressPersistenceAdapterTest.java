package com.soap.soap.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.service.ReaderTextTokenizer;
import com.soap.soap.application.service.ReadingProgressCalculator;
import com.soap.soap.application.service.TextReaderPaginationService;
import com.soap.soap.application.service.TextWordProcessor;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.ReadingProgressStatus;
import com.soap.soap.infrastructure.persistence.repository.JpaReadingProgressRepository;
import com.soap.soap.infrastructure.persistence.repository.JpaReadingProgressRepository.ContinueReadingView;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class ReadingProgressPersistenceAdapterTest {

  private JpaReadingProgressRepository repository;
  private TextReaderPaginationService paginationService;
  private ReadingProgressCalculator progressCalculator;
  private ReadingProgressPersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    repository = mock(JpaReadingProgressRepository.class);
    var wordProcessor = new TextWordProcessor();
    var tokenizer = new ReaderTextTokenizer(wordProcessor);
    paginationService = new TextReaderPaginationService(tokenizer);
    progressCalculator = new ReadingProgressCalculator();
    adapter =
        new ReadingProgressPersistenceAdapter(repository, paginationService, progressCalculator);
  }

  @Test
  @DisplayName("1. findInProgressReadings executes exactly one query without N+1 repository calls")
  void findInProgressReadingsExecutesSingleQueryWithoutNPlusOne() {
    var userId = UUID.randomUUID();
    var pageRequest = new PageRequest(0, 10);

    var view1 =
        mockView(
            UUID.randomUUID(),
            "Story 1",
            ReadingProgressStatus.IN_PROGRESS,
            1,
            1,
            generateWords(300));
    var view2 =
        mockView(
            UUID.randomUUID(),
            "Story 2",
            ReadingProgressStatus.IN_PROGRESS,
            null,
            null,
            generateWords(300));

    when(repository.findInProgressReadings(eq(userId), any(Pageable.class)))
        .thenReturn(
            new PageImpl<>(
                List.of(view1, view2), org.springframework.data.domain.PageRequest.of(0, 10), 2));

    var result = adapter.findInProgressReadings(userId, pageRequest);

    assertThat(result.content()).hasSize(2);
    assertThat(result.content().get(0).progressPercentage()).isNotNull();
    assertThat(result.content().get(1).progressPercentage()).isNull();

    // Verify exactly ONE call to repository and NO other repository calls
    verify(repository).findInProgressReadings(eq(userId), any(Pageable.class));
    verifyNoMoreInteractions(repository);
  }

  @Test
  @DisplayName("2. Content revision produces updated totalParts and progressPercentage dynamically")
  void contentRevisionUpdatesTotalPartsDynamically() {
    var userId = UUID.randomUUID();
    var pageRequest = new PageRequest(0, 10);
    var readingId = UUID.randomUUID();

    // Original content: 150 words -> 1 part. Position 1 -> 99% (capped for IN_PROGRESS)
    var viewBefore =
        mockView(
            readingId,
            "Changing Story",
            ReadingProgressStatus.IN_PROGRESS,
            1,
            1,
            generateWords(150));
    when(repository.findInProgressReadings(eq(userId), any(Pageable.class)))
        .thenReturn(
            new PageImpl<>(
                List.of(viewBefore), org.springframework.data.domain.PageRequest.of(0, 10), 1));

    var resultBefore = adapter.findInProgressReadings(userId, pageRequest);
    assertThat(resultBefore.content().get(0).progressPercentage()).isEqualTo(99);

    // Revised content: expanded to 3 paragraphs of 90, 80, 110 words = 2 parts total. Position 1 ->
    // 50%
    var p1 = generateWords(90);
    var p2 = generateWords(80);
    var p3 = generateWords(110);
    var revisedContent = String.join("\n\n", p1, p2, p3);

    var viewAfter =
        mockView(
            readingId, "Changing Story", ReadingProgressStatus.IN_PROGRESS, 1, 1, revisedContent);
    when(repository.findInProgressReadings(eq(userId), any(Pageable.class)))
        .thenReturn(
            new PageImpl<>(
                List.of(viewAfter), org.springframework.data.domain.PageRequest.of(0, 10), 1));

    var resultAfter = adapter.findInProgressReadings(userId, pageRequest);
    assertThat(resultAfter.content().get(0).progressPercentage()).isEqualTo(50);
  }

  private ContinueReadingView mockView(
      UUID readingId,
      String title,
      ReadingProgressStatus status,
      Integer currentPartOrdinal,
      Integer paginationVersion,
      String content) {
    var view = mock(ContinueReadingView.class);
    when(view.getReadingId()).thenReturn(readingId);
    when(view.getTitle()).thenReturn(title);
    when(view.getOrigin()).thenReturn(ReadingOrigin.PLATFORM);
    when(view.getProgressStatus()).thenReturn(status);
    when(view.getCoverKey()).thenReturn("cover");
    when(view.getEditorialLevel()).thenReturn(EditorialLevel.B1);
    when(view.getCategory()).thenReturn("Culture");
    when(view.getStartedAt()).thenReturn(LocalDateTime.now());
    when(view.getShortDescription()).thenReturn("Short desc");
    when(view.getCurrentPartOrdinal()).thenReturn(currentPartOrdinal);
    when(view.getPaginationVersion()).thenReturn(paginationVersion);
    when(view.getContent()).thenReturn(content);
    return view;
  }

  private String generateWords(int count) {
    return java.util.stream.IntStream.range(0, count)
            .mapToObj(i -> "word" + i)
            .collect(java.util.stream.Collectors.joining(" "))
        + ".";
  }
}
