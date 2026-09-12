package com.soap.soap.application.command;

import java.util.List;
import java.util.UUID;

public record SubmitComprehensionAttemptCommand(
    UUID readingId, UUID submissionId, List<AnswerSubmission> answers, Integer selectionVersion) {

  public SubmitComprehensionAttemptCommand(
      UUID readingId, UUID submissionId, List<AnswerSubmission> answers) {
    this(readingId, submissionId, answers, null);
  }
}
