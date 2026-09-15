package com.soap.soap.application.model;

import com.soap.soap.domain.model.EditorialStatus;
import java.util.UUID;

public record IngestEditorialReadingResult(
    UUID readingId, boolean created, EditorialStatus status, int questionsCount) {}
