package com.soap.soap.application.model;

import com.soap.soap.domain.model.QuestionType;
import java.util.List;
import java.util.UUID;

public record ComprehensionQuestionResult(
    UUID questionId,
    int ordinal,
    QuestionType questionType,
    String prompt,
    UUID selectedOptionId,
    UUID correctOptionId,
    boolean isCorrect,
    String explanation,
    List<ComprehensionOptionResult> options) {}
