package com.soap.soap.application.command;

import com.soap.soap.domain.model.QuestionType;
import java.util.List;

public record EditorialQuestionCommand(
    int ordinal,
    QuestionType questionType,
    String prompt,
    String explanation,
    List<EditorialOptionCommand> options) {}
