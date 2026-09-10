package com.soap.soap.infrastructure.soap.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.model.ContinueReadingItem;
import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.port.in.ListContinueReadingPort;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.ReadingOrigin;
import com.soap.soap.domain.model.ReadingProgressStatus;
import com.soap.soap.infrastructure.soap.generated.ListContinueReadingRequest;
import com.soap.soap.infrastructure.soap.mapper.ListContinueReadingSoapMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListContinueReadingEndpointTest {
  @Mock private ListContinueReadingPort port;

  @Test
  void serializesOriginsProgressCoverAndNullableEditorialMetadata() {
    var request = new ListContinueReadingRequest();
    request.setPage(0);
    request.setSize(10);
    var startedAt = LocalDateTime.parse("2026-09-04T12:00:00");
    var platform =
        new ContinueReadingItem(
            UUID.randomUUID(),
            "Platform",
            ReadingOrigin.PLATFORM,
            ReadingProgressStatus.IN_PROGRESS,
            "platform-cover",
            EditorialLevel.B1,
            "Science",
            startedAt);
    var user =
        new ContinueReadingItem(
            UUID.randomUUID(),
            "User",
            ReadingOrigin.USER,
            ReadingProgressStatus.IN_PROGRESS,
            null,
            null,
            null,
            startedAt.minusMinutes(1));
    when(port.listContinueReading(new PageRequest(0, 10)))
        .thenReturn(new PageResult<>(List.of(platform, user), 0, 10, 2));

    var response =
        new ListContinueReadingEndpoint(port, new ListContinueReadingSoapMapper())
            .listContinueReading(request);

    assertThat(response.getReadings()).hasSize(2);
    assertThat(response.getReadings().getFirst())
        .satisfies(
            item -> {
              assertThat(item.getOrigin().value()).isEqualTo("PLATFORM");
              assertThat(item.getProgressStatus().value()).isEqualTo("IN_PROGRESS");
              assertThat(item.getCoverKey()).isEqualTo("platform-cover");
              assertThat(item.getEditorialLevel().value()).isEqualTo("B1");
              assertThat(item.getCategory()).isEqualTo("Science");
              assertThat(item.getStartedAt()).isNotNull();
            });
    assertThat(response.getReadings().get(1))
        .satisfies(
            item -> {
              assertThat(item.getOrigin().value()).isEqualTo("USER");
              assertThat(item.getCoverKey()).isNull();
              assertThat(item.getEditorialLevel()).isNull();
              assertThat(item.getCategory()).isNull();
            });
    verify(port).listContinueReading(new PageRequest(0, 10));
  }
}
