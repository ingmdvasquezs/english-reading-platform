package com.soap.soap.infrastructure.soap.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.PlatformReadingHistoryItem;
import com.soap.soap.application.port.in.ListPlatformReadingHistoryPort;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.domain.model.ReadingProgressStatus;
import com.soap.soap.infrastructure.soap.generated.EditorialLevelType;
import com.soap.soap.infrastructure.soap.generated.ListPlatformReadingHistoryRequest;
import com.soap.soap.infrastructure.soap.generated.ReadingProgressStatusType;
import com.soap.soap.infrastructure.soap.mapper.ListPlatformReadingHistorySoapMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListPlatformReadingHistoryEndpointTest {
  @Mock private ListPlatformReadingHistoryPort port;

  @Test
  void mapsRequestAndResponseCorrectly() {
    var request = new ListPlatformReadingHistoryRequest();
    request.setPage(0);
    request.setSize(10);

    var readingId1 = UUID.randomUUID();
    var readingId2 = UUID.randomUUID();
    var item1 =
        new PlatformReadingHistoryItem(
            readingId1,
            "History 1",
            EditorialLevel.A2,
            "History",
            "covers/h1.jpg",
            ReadingProgressStatus.IN_PROGRESS);
    var item2 =
        new PlatformReadingHistoryItem(
            readingId2,
            "History 2",
            EditorialLevel.B2,
            "Technology",
            null,
            ReadingProgressStatus.COMPLETED);

    when(port.listPlatformReadingHistory(new PageRequest(0, 10)))
        .thenReturn(new PageResult<>(List.of(item1, item2), 0, 10, 2));

    var mapper = new ListPlatformReadingHistorySoapMapper();
    var endpoint = new ListPlatformReadingHistoryEndpoint(port, mapper);

    var response = endpoint.listPlatformReadingHistory(request);

    assertThat(response.getPage()).isEqualTo(0);
    assertThat(response.getSize()).isEqualTo(10);
    assertThat(response.getTotalElements()).isEqualTo(2);
    assertThat(response.getReadings()).hasSize(2);

    var r1 = response.getReadings().get(0);
    assertThat(r1.getReadingId()).isEqualTo(readingId1.toString());
    assertThat(r1.getTitle()).isEqualTo("History 1");
    assertThat(r1.getEditorialLevel()).isEqualTo(EditorialLevelType.A_2);
    assertThat(r1.getCategory()).isEqualTo("History");
    assertThat(r1.getCoverKey()).isEqualTo("covers/h1.jpg");
    assertThat(r1.getProgressStatus()).isEqualTo(ReadingProgressStatusType.IN_PROGRESS);

    var r2 = response.getReadings().get(1);
    assertThat(r2.getReadingId()).isEqualTo(readingId2.toString());
    assertThat(r2.getTitle()).isEqualTo("History 2");
    assertThat(r2.getEditorialLevel()).isEqualTo(EditorialLevelType.B_2);
    assertThat(r2.getCategory()).isEqualTo("Technology");
    assertThat(r2.getCoverKey()).isNull();
    assertThat(r2.getProgressStatus()).isEqualTo(ReadingProgressStatusType.COMPLETED);

    verify(port).listPlatformReadingHistory(new PageRequest(0, 10));
  }
}
