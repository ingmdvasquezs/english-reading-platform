package com.soap.soap.application.command;

import com.soap.soap.domain.model.ReadingProgressStatus;
import java.util.UUID;

public record UpdateReadingProgressCommand(
    UUID readingId,
    ReadingProgressStatus progressStatus,
    Integer currentPartOrdinal,
    Integer paginationVersion) {}
