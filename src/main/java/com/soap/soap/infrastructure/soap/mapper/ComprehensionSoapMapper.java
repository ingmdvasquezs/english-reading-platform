package com.soap.soap.infrastructure.soap.mapper;

import com.soap.soap.application.command.AnswerSubmission;
import com.soap.soap.application.command.SubmitComprehensionAttemptCommand;
import com.soap.soap.application.model.ComprehensionAttemptResult;
import com.soap.soap.application.model.ComprehensionQuizView;
import com.soap.soap.application.model.LatestComprehensionResult;
import com.soap.soap.infrastructure.soap.generated.ComprehensionAttemptResultType;
import com.soap.soap.infrastructure.soap.generated.ComprehensionQuestionResultOptionType;
import com.soap.soap.infrastructure.soap.generated.ComprehensionQuestionResultType;
import com.soap.soap.infrastructure.soap.generated.ComprehensionQuizOptionType;
import com.soap.soap.infrastructure.soap.generated.ComprehensionQuizQuestionType;
import com.soap.soap.infrastructure.soap.generated.GetLatestComprehensionResultRequest;
import com.soap.soap.infrastructure.soap.generated.GetLatestComprehensionResultResponse;
import com.soap.soap.infrastructure.soap.generated.GetReadingComprehensionQuizRequest;
import com.soap.soap.infrastructure.soap.generated.GetReadingComprehensionQuizResponse;
import com.soap.soap.infrastructure.soap.generated.QuestionTypeType;
import com.soap.soap.infrastructure.soap.generated.SubmitComprehensionAttemptRequest;
import com.soap.soap.infrastructure.soap.generated.SubmitComprehensionAttemptResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ComprehensionSoapMapper extends SoapMapperSupport {

  public UUID toReadingId(GetReadingComprehensionQuizRequest request) {
    return parseUuid(request.getReadingId(), "readingId");
  }

  public UUID toSubmissionId(GetReadingComprehensionQuizRequest request) {
    if (request.getSubmissionId() == null || request.getSubmissionId().isBlank()) {
      return null;
    }
    return parseUuid(request.getSubmissionId(), "submissionId");
  }

  public GetReadingComprehensionQuizResponse toQuizResponse(ComprehensionQuizView view) {
    var response = new GetReadingComprehensionQuizResponse();
    response.setReadingId(view.readingId().toString());
    response.setAvailable(view.available());
    if (view.selectionVersion() != null) {
      response.setSelectionVersion(view.selectionVersion());
    }
    for (var q : view.questions()) {
      var qType = new ComprehensionQuizQuestionType();
      qType.setQuestionId(q.questionId().toString());
      qType.setOrdinal(q.ordinal());
      qType.setQuestionType(QuestionTypeType.fromValue(q.questionType().name()));
      qType.setPrompt(q.prompt());
      for (var o : q.options()) {
        var oType = new ComprehensionQuizOptionType();
        oType.setOptionId(o.optionId().toString());
        oType.setOrdinal(o.ordinal());
        oType.setContent(o.content());
        qType.getOptions().add(oType);
      }
      response.getQuestions().add(qType);
    }
    return response;
  }

  public SubmitComprehensionAttemptCommand toCommand(SubmitComprehensionAttemptRequest request) {
    var readingId = parseUuid(request.getReadingId(), "readingId");
    var submissionId = parseUuid(request.getSubmissionId(), "submissionId");
    List<AnswerSubmission> answers = new ArrayList<>();
    if (request.getAnswers() != null) {
      for (var ans : request.getAnswers()) {
        answers.add(
            new AnswerSubmission(
                parseUuid(ans.getQuestionId(), "questionId"),
                parseUuid(ans.getSelectedOptionId(), "selectedOptionId")));
      }
    }
    return new SubmitComprehensionAttemptCommand(
        readingId, submissionId, answers, request.getSelectionVersion());
  }

  public SubmitComprehensionAttemptResponse toSubmitResponse(ComprehensionAttemptResult result) {
    var response = new SubmitComprehensionAttemptResponse();
    response.setAttempt(toAttemptResultType(result));
    return response;
  }

  public UUID toLatestReadingId(GetLatestComprehensionResultRequest request) {
    return parseUuid(request.getReadingId(), "readingId");
  }

  public GetLatestComprehensionResultResponse toLatestResultResponse(
      LatestComprehensionResult result) {
    var response = new GetLatestComprehensionResultResponse();
    response.setReadingId(result.readingId().toString());
    response.setHasAttempt(result.hasAttempt());
    if (result.hasAttempt() && result.attempt() != null) {
      response.setAttempt(toAttemptResultType(result.attempt()));
    }
    return response;
  }

  private ComprehensionAttemptResultType toAttemptResultType(ComprehensionAttemptResult result) {
    var attemptType = new ComprehensionAttemptResultType();
    attemptType.setAttemptId(result.attemptId().toString());
    attemptType.setReadingId(result.readingId().toString());
    attemptType.setSubmissionId(result.submissionId().toString());
    attemptType.setScorePercentage(result.scorePercentage());
    attemptType.setCorrectAnswersCount(result.correctAnswersCount());
    attemptType.setTotalQuestionsCount(result.totalQuestionsCount());
    attemptType.setSubmittedAt(toXmlDate(result.submittedAt()));

    for (var q : result.questions()) {
      var qr = new ComprehensionQuestionResultType();
      qr.setQuestionId(q.questionId().toString());
      qr.setOrdinal(q.ordinal());
      qr.setQuestionType(QuestionTypeType.fromValue(q.questionType().name()));
      qr.setPrompt(q.prompt());
      if (q.selectedOptionId() != null) {
        qr.setSelectedOptionId(q.selectedOptionId().toString());
      }
      if (q.correctOptionId() != null) {
        qr.setCorrectOptionId(q.correctOptionId().toString());
      }
      qr.setIsCorrect(q.isCorrect());
      qr.setExplanation(q.explanation());
      for (var o : q.options()) {
        var opt = new ComprehensionQuestionResultOptionType();
        opt.setOptionId(o.optionId().toString());
        opt.setOrdinal(o.ordinal());
        opt.setContent(o.content());
        qr.getOptions().add(opt);
      }
      attemptType.getQuestions().add(qr);
    }
    return attemptType;
  }
}
