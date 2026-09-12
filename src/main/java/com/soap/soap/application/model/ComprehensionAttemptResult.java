package com.soap.soap.application.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ComprehensionAttemptResult(
    UUID attemptId,
    UUID readingId,
    UUID submissionId,
    BigDecimal scorePercentage,
    int correctAnswersCount,
    int totalQuestionsCount,
    LocalDateTime submittedAt,
    List<ComprehensionQuestionResult> questions) {}
