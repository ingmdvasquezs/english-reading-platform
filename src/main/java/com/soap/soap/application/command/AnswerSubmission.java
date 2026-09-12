package com.soap.soap.application.command;

import java.util.UUID;

public record AnswerSubmission(UUID questionId, UUID selectedOptionId) {}
