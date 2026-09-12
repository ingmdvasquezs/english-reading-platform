package com.soap.soap.infrastructure.soap.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.soap.soap.application.command.AnswerSubmission;
import com.soap.soap.application.command.SubmitComprehensionAttemptCommand;
import com.soap.soap.application.model.ComprehensionAttemptResult;
import com.soap.soap.application.model.ComprehensionOptionResult;
import com.soap.soap.application.model.ComprehensionQuestionResult;
import com.soap.soap.application.model.ComprehensionQuizOptionView;
import com.soap.soap.application.model.ComprehensionQuizQuestionView;
import com.soap.soap.application.model.ComprehensionQuizView;
import com.soap.soap.application.model.LatestComprehensionResult;
import com.soap.soap.application.port.in.GetLatestComprehensionResultPort;
import com.soap.soap.application.port.in.GetReadingComprehensionQuizPort;
import com.soap.soap.application.port.in.SubmitComprehensionAttemptPort;
import com.soap.soap.domain.model.QuestionType;
import com.soap.soap.infrastructure.soap.generated.GetLatestComprehensionResultRequest;
import com.soap.soap.infrastructure.soap.generated.GetReadingComprehensionQuizRequest;
import com.soap.soap.infrastructure.soap.generated.SubmitComprehensionAttemptRequest;
import com.soap.soap.infrastructure.soap.mapper.ComprehensionSoapMapper;
import jakarta.xml.bind.JAXBContext;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ComprehensionSoapContractTest {

  @Mock private GetReadingComprehensionQuizPort getQuizPort;
  @Mock private SubmitComprehensionAttemptPort submitAttemptPort;
  @Mock private GetLatestComprehensionResultPort latestResultPort;

  private ComprehensionSoapMapper mapper;
  private GetReadingComprehensionQuizEndpoint getQuizEndpoint;
  private SubmitComprehensionAttemptEndpoint submitAttemptEndpoint;
  private GetLatestComprehensionResultEndpoint latestResultEndpoint;

  @BeforeEach
  void setUp() {
    mapper = new ComprehensionSoapMapper();
    getQuizEndpoint = new GetReadingComprehensionQuizEndpoint(getQuizPort, mapper);
    submitAttemptEndpoint = new SubmitComprehensionAttemptEndpoint(submitAttemptPort, mapper);
    latestResultEndpoint = new GetLatestComprehensionResultEndpoint(latestResultPort, mapper);
  }

  @Test
  void quizResponseDoesNotContainIsCorrectOrExplanationInRawXml() throws Exception {
    UUID readingId = UUID.randomUUID();
    UUID questionId = UUID.randomUUID();
    UUID optionId = UUID.randomUUID();

    var quizView =
        new ComprehensionQuizView(
            readingId,
            true,
            List.of(
                new ComprehensionQuizQuestionView(
                    questionId,
                    1,
                    QuestionType.FACTUAL,
                    "What color was the scarf?",
                    List.of(new ComprehensionQuizOptionView(optionId, 1, "Blue")))));

    when(getQuizPort.getQuiz(readingId)).thenReturn(quizView);

    var request = new GetReadingComprehensionQuizRequest();
    request.setReadingId(readingId.toString());

    var response = getQuizEndpoint.getQuiz(request);

    // Marshal to XML string to inspect raw contract
    var jaxbContext = JAXBContext.newInstance(response.getClass());
    var marshaller = jaxbContext.createMarshaller();
    var writer = new StringWriter();
    marshaller.marshal(response, writer);
    String rawXml = writer.toString();

    // Verify confidential fields are completely absent from XML
    assertThat(rawXml).doesNotContain("isCorrect");
    assertThat(rawXml).doesNotContain("explanation");
    assertThat(rawXml).doesNotContain("correctOptionId");
    assertThat(rawXml).contains("What color was the scarf?");
    assertThat(rawXml).contains("Blue");
    assertThat(rawXml).contains("<available>true</available>");
  }

  @Test
  void submitResponseIncludesScoreAndFeedback() {
    UUID readingId = UUID.randomUUID();
    UUID submissionId = UUID.randomUUID();
    UUID attemptId = UUID.randomUUID();
    UUID questionId = UUID.randomUUID();
    UUID selectedOptionId = UUID.randomUUID();
    UUID correctOptionId = selectedOptionId;

    var attemptResult =
        new ComprehensionAttemptResult(
            attemptId,
            readingId,
            submissionId,
            new BigDecimal("100.00"),
            1,
            1,
            LocalDateTime.now(),
            List.of(
                new ComprehensionQuestionResult(
                    questionId,
                    1,
                    QuestionType.FACTUAL,
                    "What color was the scarf?",
                    selectedOptionId,
                    correctOptionId,
                    true,
                    "Her grandmother made a blue scarf.",
                    List.of(new ComprehensionOptionResult(selectedOptionId, 1, "Blue")))));

    when(submitAttemptPort.submitAttempt(
            new SubmitComprehensionAttemptCommand(
                readingId,
                submissionId,
                List.of(new AnswerSubmission(questionId, selectedOptionId)))))
        .thenReturn(attemptResult);

    var request = new SubmitComprehensionAttemptRequest();
    request.setReadingId(readingId.toString());
    request.setSubmissionId(submissionId.toString());
    var answerInput =
        new com.soap.soap.infrastructure.soap.generated.ComprehensionAnswerInputType();
    answerInput.setQuestionId(questionId.toString());
    answerInput.setSelectedOptionId(selectedOptionId.toString());
    request.getAnswers().add(answerInput);

    var response = submitAttemptEndpoint.submitAttempt(request);

    assertThat(response.getAttempt()).isNotNull();
    assertThat(response.getAttempt().getAttemptId()).isEqualTo(attemptId.toString());
    assertThat(response.getAttempt().getScorePercentage()).isEqualTo(new BigDecimal("100.00"));
    assertThat(response.getAttempt().getQuestions()).hasSize(1);
    var qResult = response.getAttempt().getQuestions().get(0);
    assertThat(qResult.isIsCorrect()).isTrue();
    assertThat(qResult.getExplanation()).isEqualTo("Her grandmother made a blue scarf.");
    assertThat(qResult.getCorrectOptionId()).isEqualTo(correctOptionId.toString());
  }

  @Test
  void latestResultResponseMapsAttemptCorrectly() {
    UUID readingId = UUID.randomUUID();
    when(latestResultPort.getLatestResult(readingId))
        .thenReturn(new LatestComprehensionResult(readingId, false, null));

    var request = new GetLatestComprehensionResultRequest();
    request.setReadingId(readingId.toString());

    var response = latestResultEndpoint.getLatestResult(request);

    assertThat(response.getReadingId()).isEqualTo(readingId.toString());
    assertThat(response.isHasAttempt()).isFalse();
    assertThat(response.getAttempt()).isNull();
  }
}
