package com.soap.soap.application.model;

import java.util.UUID;

public record UpdatePublishedEditorialContentResult(
    UUID readingId,
    String adaptationGroupKey,
    boolean contentUpdated,
    boolean quizUpdated,
    int lexicalFrequencyCount) {}
