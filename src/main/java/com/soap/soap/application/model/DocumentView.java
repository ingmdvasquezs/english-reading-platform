package com.soap.soap.application.model;

import com.soap.soap.domain.model.DocumentFormat;
import com.soap.soap.domain.model.DocumentImportStatus;
import com.soap.soap.domain.model.DocumentProgressStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentView(
    UUID id,
    String title,
    String author,
    String language,
    DocumentFormat format,
    DocumentImportStatus status,
    String failureReason,
    boolean coverAvailable,
    long totalSections,
    long totalUnits,
    DocumentProgressStatus progressStatus,
    LocalDateTime lastReadAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
