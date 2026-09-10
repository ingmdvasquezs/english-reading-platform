package com.soap.soap.application.model;

import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.ReadingProgressStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record ContinueReadingItem(
    UUID readingId,
    String title,
    ReadingOrigin origin,
    ReadingProgressStatus progressStatus,
    String coverKey,
    EditorialLevel editorialLevel,
    String category,
    LocalDateTime startedAt) {}
