package com.soap.soap.application.model;

import java.util.UUID;

public record LatestComprehensionResult(
    UUID readingId, boolean hasAttempt, ComprehensionAttemptResult attempt) {}
