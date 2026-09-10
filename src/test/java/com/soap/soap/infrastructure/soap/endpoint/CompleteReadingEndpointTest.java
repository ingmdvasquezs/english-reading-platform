package com.soap.soap.infrastructure.soap.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.port.in.CompleteReadingPort;
import com.soap.soap.domain.model.ReadingProgress;
import com.soap.soap.infrastructure.soap.generated.CompleteReadingRequest;
import com.soap.soap.infrastructure.soap.generated.ReadingProgressStatusType;
import com.soap.soap.infrastructure.soap.mapper.CompleteReadingSoapMapper;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompleteReadingEndpointTest {
  @Mock private CompleteReadingPort port;

  @Test
  void mapsAuthenticatedCompletionWithoutAcceptingAUserId() {
    var readingId = UUID.randomUUID();
    var userId = UUID.randomUUID();
    var startedAt = LocalDateTime.parse("2026-08-30T10:00:00");
    var completedAt = startedAt.plusMinutes(5);
    var request = new CompleteReadingRequest();
    request.setReadingId(readingId.toString());
    when(port.completeReading(readingId))
        .thenReturn(ReadingProgress.completed(userId, readingId, startedAt, completedAt));

    var response =
        new CompleteReadingEndpoint(port, new CompleteReadingSoapMapper()).completeReading(request);

    assertThat(CompleteReadingRequest.class.getMethods())
        .noneMatch(method -> method.getName().equals("getUserId"));
    assertThat(response.getReadingId()).isEqualTo(readingId.toString());
    assertThat(response.getStatus()).isEqualTo(ReadingProgressStatusType.COMPLETED);
    assertThat(response.getStartedAt().toGregorianCalendar().toZonedDateTime().toLocalDateTime())
        .isEqualTo(startedAt);
    assertThat(response.getCompletedAt().toGregorianCalendar().toZonedDateTime().toLocalDateTime())
        .isEqualTo(completedAt);
    verify(port).completeReading(readingId);
  }
}
