package com.soap.soap.infrastructure.soap.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.soap.soap.application.command.UpdateReadingProgressCommand;
import com.soap.soap.application.port.in.UpdateReadingProgressPort;
import com.soap.soap.domain.model.ReadingProgress;
import com.soap.soap.domain.model.ReadingProgressStatus;
import com.soap.soap.infrastructure.soap.generated.ReadingProgressStatusType;
import com.soap.soap.infrastructure.soap.generated.UpdateReadingProgressRequest;
import com.soap.soap.infrastructure.soap.mapper.UpdateReadingProgressSoapMapper;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateReadingProgressEndpointTest {
  @Mock UpdateReadingProgressPort port;

  @Test
  void mapsPositionInBothDirections() {
    var readingId = UUID.randomUUID();
    var request = new UpdateReadingProgressRequest();
    request.setReadingId(readingId.toString());
    request.setProgressStatus(ReadingProgressStatusType.IN_PROGRESS);
    request.setCurrentPartOrdinal(8);
    request.setPaginationVersion(1);
    var command =
        new UpdateReadingProgressCommand(readingId, ReadingProgressStatus.IN_PROGRESS, 8, 1);
    when(port.updateReadingProgress(command))
        .thenReturn(
            new ReadingProgress(
                UUID.randomUUID(),
                UUID.randomUUID(),
                readingId,
                ReadingProgressStatus.IN_PROGRESS,
                LocalDateTime.parse("2026-09-09T12:00:00"),
                null,
                8,
                1));

    var response =
        new UpdateReadingProgressEndpoint(port, new UpdateReadingProgressSoapMapper())
            .updateReadingProgress(request);

    assertThat(response.getCurrentPartOrdinal()).isEqualTo(8);
    assertThat(response.getPaginationVersion()).isEqualTo(1);
    assertThat(response.getProgressStatus()).isEqualTo(ReadingProgressStatusType.IN_PROGRESS);
  }
}
