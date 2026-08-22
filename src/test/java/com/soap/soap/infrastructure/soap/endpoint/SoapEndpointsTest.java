package com.soap.soap.infrastructure.soap.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.exception.ReadingAccessDeniedException;
import com.soap.soap.application.exception.ReadingNotFoundException;
import com.soap.soap.application.model.AccessToken;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.ReadingSummary;
import com.soap.soap.application.port.in.AddWordToVocabularyPort;
import com.soap.soap.application.port.in.AnalyzeReadingPort;
import com.soap.soap.application.port.in.ChangeVocabularyStatusPort;
import com.soap.soap.application.port.in.GetReadingPort;
import com.soap.soap.application.port.in.ListUserReadingsPort;
import com.soap.soap.application.port.in.ListUserVocabularyPort;
import com.soap.soap.application.port.in.LoginPort;
import com.soap.soap.application.port.in.RegisterUserPort;
import com.soap.soap.domain.model.AnalyzedWord;
import com.soap.soap.domain.model.Reading;
import com.soap.soap.domain.model.ReadingAnalysis;
import com.soap.soap.domain.model.User;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.model.VocabularyStatus;
import com.soap.soap.domain.model.Word;
import com.soap.soap.infrastructure.soap.exception.InvalidSoapRequestException;
import com.soap.soap.infrastructure.soap.generated.AddWordToVocabularyRequest;
import com.soap.soap.infrastructure.soap.generated.AnalyzeReadingRequest;
import com.soap.soap.infrastructure.soap.generated.ChangeVocabularyStatusRequest;
import com.soap.soap.infrastructure.soap.generated.GetReadingRequest;
import com.soap.soap.infrastructure.soap.generated.ListUserReadingsRequest;
import com.soap.soap.infrastructure.soap.generated.ListUserVocabularyRequest;
import com.soap.soap.infrastructure.soap.generated.LoginRequest;
import com.soap.soap.infrastructure.soap.generated.RegisterUserRequest;
import com.soap.soap.infrastructure.soap.generated.VocabularyStatusType;
import com.soap.soap.infrastructure.soap.mapper.AddWordToVocabularySoapMapper;
import com.soap.soap.infrastructure.soap.mapper.AnalyzeReadingSoapMapper;
import com.soap.soap.infrastructure.soap.mapper.ChangeVocabularyStatusSoapMapper;
import com.soap.soap.infrastructure.soap.mapper.GetReadingSoapMapper;
import com.soap.soap.infrastructure.soap.mapper.ListUserReadingsSoapMapper;
import com.soap.soap.infrastructure.soap.mapper.ListUserVocabularySoapMapper;
import com.soap.soap.infrastructure.soap.mapper.UserAuthenticationSoapMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SoapEndpointsTest {
  @Mock private GetReadingPort getReadingPort;
  @Mock private ListUserReadingsPort listReadingsPort;
  @Mock private AddWordToVocabularyPort addWordPort;
  @Mock private ChangeVocabularyStatusPort changeStatusPort;
  @Mock private ListUserVocabularyPort listVocabularyPort;
  @Mock private AnalyzeReadingPort analyzePort;
  @Mock private RegisterUserPort registerUserPort;
  @Mock private LoginPort loginPort;

  private User user;
  private Reading reading;
  private UserVocabulary vocabulary;

  @Test
  void protectedSoapRequestsDoNotExposeAUserIdProperty() {
    assertThat(
            List.of(
                com.soap.soap.infrastructure.soap.generated.RegisterReadingRequest.class,
                ListUserReadingsRequest.class,
                AddWordToVocabularyRequest.class,
                ChangeVocabularyStatusRequest.class,
                ListUserVocabularyRequest.class,
                AnalyzeReadingRequest.class))
        .allSatisfy(
            type ->
                assertThat(type.getMethods())
                    .noneMatch(method -> method.getName().equals("getUserId")));
  }

  @Test
  void registersAUserThroughThePublicSoapOperation() {
    var request = new RegisterUserRequest();
    request.setName("Ada");
    request.setEmail("ada@example.com");
    request.setPassword("secret123");
    var registered =
        new User(user.id(), "Ada", "ada@example.com", "not-exposed", LocalDateTime.now());
    when(registerUserPort.registerUser(org.mockito.ArgumentMatchers.any())).thenReturn(registered);
    var response =
        new RegisterUserEndpoint(registerUserPort, new UserAuthenticationSoapMapper())
            .register(request);
    assertThat(response.getEmail()).isEqualTo("ada@example.com");
    assertThat(response.toString()).doesNotContain("secret123", "not-exposed");
  }

  @Test
  void logsInThroughThePublicSoapOperation() {
    var request = new LoginRequest();
    request.setEmail("ada@example.com");
    request.setPassword("secret123");
    when(loginPort.login(org.mockito.ArgumentMatchers.any()))
        .thenReturn(new AccessToken("signed-jwt", "Bearer", 3600));
    var response = new LoginEndpoint(loginPort, new UserAuthenticationSoapMapper()).login(request);
    assertThat(response.getAccessToken()).isEqualTo("signed-jwt");
    assertThat(response.getTokenType()).isEqualTo("Bearer");
  }

  @BeforeEach
  void setUp() {
    user = new User(UUID.randomUUID(), "Ada", "ada@example.com");
    reading =
        new Reading(UUID.randomUUID(), user, "Title", "Hello world", "en", LocalDateTime.now());
    vocabulary =
        new UserVocabulary(
            UUID.randomUUID(),
            user,
            new Word(UUID.randomUUID(), "hello", "en"),
            VocabularyStatus.NEW,
            LocalDateTime.now(),
            null);
  }

  @Test
  void getsAReadingOnHappyPath() {
    var request = new GetReadingRequest();
    request.setReadingId(reading.id().toString());
    when(getReadingPort.getReading(reading.id())).thenReturn(reading);

    var response =
        new GetReadingEndpoint(getReadingPort, new GetReadingSoapMapper()).getReading(request);

    assertThat(response.getReading().getReadingId()).isEqualTo(reading.id().toString());
    assertThat(response.getReading().getContent()).isEqualTo("Hello world");
  }

  @Test
  void rejectsAnInvalidTransportIdentifier() {
    var request = new GetReadingRequest();
    request.setReadingId("not-a-uuid");

    assertThatThrownBy(
            () ->
                new GetReadingEndpoint(getReadingPort, new GetReadingSoapMapper())
                    .getReading(request))
        .isInstanceOf(InvalidSoapRequestException.class);
  }

  @Test
  void propagatesNotFoundForTheSoapFaultResolver() {
    var id = UUID.randomUUID();
    var request = new GetReadingRequest();
    request.setReadingId(id.toString());
    when(getReadingPort.getReading(id)).thenThrow(new ReadingNotFoundException(id));

    assertThatThrownBy(
            () ->
                new GetReadingEndpoint(getReadingPort, new GetReadingSoapMapper())
                    .getReading(request))
        .isInstanceOf(ReadingNotFoundException.class);
  }

  @Test
  void preservesReadingPagination() {
    var request = new ListUserReadingsRequest();
    request.setPage(2);
    request.setSize(5);
    var summary =
        new ReadingSummary(reading.id(), reading.title(), reading.language(), reading.createdAt());
    var page = new PageResult<>(List.of(summary), 2, 5, 11);
    when(listReadingsPort.listUserReadings(new PageRequest(2, 5))).thenReturn(page);
    var mapper = new ListUserReadingsSoapMapper();

    var response = new ListUserReadingsEndpoint(listReadingsPort, mapper).listUserReadings(request);

    assertThat(response.getPage()).isEqualTo(2);
    assertThat(response.getSize()).isEqualTo(5);
    assertThat(response.getTotalElements()).isEqualTo(11);
    assertThat(response.getReadings()).hasSize(1);
    assertThat(response.getReadings().getFirst().getClass().getMethods())
        .noneMatch(method -> method.getName().equals("getContent"));
  }

  @Test
  void addsAWordThroughTheInputPort() {
    var request = new AddWordToVocabularyRequest();
    request.setWord("hello");
    request.setLanguage("en");
    request.setInitialStatus(VocabularyStatusType.NEW);
    when(addWordPort.addWordToVocabulary(org.mockito.ArgumentMatchers.any()))
        .thenReturn(vocabulary);

    var response =
        new AddWordToVocabularyEndpoint(addWordPort, new AddWordToVocabularySoapMapper())
            .addWordToVocabulary(request);

    assertThat(response.getEntry().getWord()).isEqualTo("hello");
    assertThat(response.getEntry().getStatus()).isEqualTo(VocabularyStatusType.NEW);
  }

  @Test
  void changesVocabularyStatusThroughTheInputPort() {
    var request = new ChangeVocabularyStatusRequest();
    request.setWordId(vocabulary.word().id().toString());
    request.setStatus(VocabularyStatusType.LEARNING);
    when(changeStatusPort.changeVocabularyStatus(vocabulary.word().id(), VocabularyStatus.LEARNING))
        .thenReturn(vocabulary);

    new ChangeVocabularyStatusEndpoint(changeStatusPort, new ChangeVocabularyStatusSoapMapper())
        .changeVocabularyStatus(request);

    verify(changeStatusPort)
        .changeVocabularyStatus(vocabulary.word().id(), VocabularyStatus.LEARNING);
  }

  @Test
  void preservesVocabularyPagination() {
    var request = new ListUserVocabularyRequest();
    request.setPage(1);
    request.setSize(20);
    when(listVocabularyPort.listUserVocabulary(new PageRequest(1, 20)))
        .thenReturn(new PageResult<>(List.of(vocabulary), 1, 20, 21));

    var response =
        new ListUserVocabularyEndpoint(listVocabularyPort, new ListUserVocabularySoapMapper())
            .listUserVocabulary(request);

    assertThat(response.getTotalElements()).isEqualTo(21);
    assertThat(response.getEntries()).hasSize(1);
  }

  @Test
  void mapsReadingAnalysisMetricsWithoutIndividualTokens() {
    var request = new AnalyzeReadingRequest();
    request.setReadingId(reading.id().toString());
    var analysis =
        new ReadingAnalysis(
            List.of(
                new AnalyzedWord("Hello", "hello", VocabularyStatus.KNOWN),
                new AnalyzedWord("world", "world", VocabularyStatus.NEW)),
            List.of());
    when(analyzePort.analyzeReading(reading.id())).thenReturn(analysis);

    var response =
        new AnalyzeReadingEndpoint(analyzePort, new AnalyzeReadingSoapMapper())
            .analyzeReading(request);

    assertThat(response.getTotalTokens()).isEqualTo(2);
    assertThat(response.getKnownWords()).isEqualTo(1);
    assertThat(response.getUnknownWords()).isEqualTo(1);
    assertThat(response.getPersonalizedCoveragePercentage()).isEqualTo(50.0);
    assertThat(response.getUnknownWordFrequencies()).isEmpty();
    assertThat(response.getClass().getMethods())
        .noneMatch(method -> method.getName().equals("getWords"));
  }

  @Test
  void propagatesAuthorizationFailureForTheSoapFaultResolver() {
    var request = new AnalyzeReadingRequest();
    request.setReadingId(reading.id().toString());
    when(analyzePort.analyzeReading(reading.id()))
        .thenThrow(new ReadingAccessDeniedException(reading.id(), user.id()));

    assertThatThrownBy(
            () ->
                new AnalyzeReadingEndpoint(analyzePort, new AnalyzeReadingSoapMapper())
                    .analyzeReading(request))
        .isInstanceOf(ReadingAccessDeniedException.class);
  }
}
