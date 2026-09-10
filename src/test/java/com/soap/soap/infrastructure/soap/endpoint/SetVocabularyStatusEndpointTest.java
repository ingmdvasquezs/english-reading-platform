package com.soap.soap.infrastructure.soap.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.command.SetVocabularyStatusCommand;
import com.soap.soap.application.port.in.SetVocabularyStatusPort;
import com.soap.soap.domain.model.User;
import com.soap.soap.domain.model.UserVocabulary;
import com.soap.soap.domain.model.VocabularyStatus;
import com.soap.soap.domain.model.Word;
import com.soap.soap.infrastructure.soap.generated.SetVocabularyStatusRequest;
import com.soap.soap.infrastructure.soap.generated.VocabularyStatusType;
import com.soap.soap.infrastructure.soap.mapper.SetVocabularyStatusSoapMapper;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SetVocabularyStatusEndpointTest {
  @Mock private SetVocabularyStatusPort port;

  @Test
  void mapsWordLanguageAndStatusWithoutAcceptingAUserId() {
    var request = new SetVocabularyStatusRequest();
    request.setWord("Learning");
    request.setLanguage("en");
    request.setStatus(VocabularyStatusType.LEARNING);
    var user = new User(UUID.randomUUID(), "Ada", "ada@example.com");
    var word = new Word(UUID.randomUUID(), "learning", "en");
    var saved =
        new UserVocabulary(
            UUID.randomUUID(), user, word, VocabularyStatus.LEARNING, LocalDateTime.now(), null);
    var command = new SetVocabularyStatusCommand("Learning", "en", VocabularyStatus.LEARNING);
    when(port.setVocabularyStatus(command)).thenReturn(saved);

    var response =
        new SetVocabularyStatusEndpoint(port, new SetVocabularyStatusSoapMapper())
            .setVocabularyStatus(request);

    assertThat(response.getEntry().getWord()).isEqualTo("learning");
    assertThat(response.getEntry().getStatus()).isEqualTo(VocabularyStatusType.LEARNING);
    assertThat(SetVocabularyStatusRequest.class.getMethods())
        .noneMatch(method -> method.getName().equals("getUserId"));
    verify(port).setVocabularyStatus(command);
  }
}
