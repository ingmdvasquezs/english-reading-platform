package com.soap.soap.application.model;

import com.soap.soap.domain.model.QuestionType;
import java.util.List;
import java.util.UUID;

public record ComprehensionQuizQuestionView(
    UUID questionId,
    int ordinal,
    QuestionType questionType,
    String prompt,
    List<ComprehensionQuizOptionView> options) {}
