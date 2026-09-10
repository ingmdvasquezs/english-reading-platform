package com.soap.soap.infrastructure.soap.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.model.PageRequest;
import com.soap.soap.application.model.PageResult;
import com.soap.soap.application.model.RecommendedPlatformReading;
import com.soap.soap.application.port.in.RecommendPlatformReadingsPort;
import com.soap.soap.domain.model.EditorialLevel;
import com.soap.soap.infrastructure.soap.generated.RecommendPlatformReadingsRequest;
import com.soap.soap.infrastructure.soap.mapper.RecommendPlatformReadingsSoapMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendPlatformReadingsEndpointTest {
  @Mock private RecommendPlatformReadingsPort port;

  @Test
  void mapsTheContractWithoutAcceptingAUserId() {
    var request = new RecommendPlatformReadingsRequest();
    request.setPage(0);
    request.setSize(10);
    var reading =
        new RecommendedPlatformReading(
            UUID.randomUUID(),
            "Recommended",
            "en",
            EditorialLevel.B1,
            "Work",
            LocalDateTime.parse("2026-08-29T12:00:00"),
            5,
            1,
            1,
            1,
            1,
            1,
            new BigDecimal("56.00"),
            new BigDecimal("80.00"));
    when(port.recommendPlatformReadings(new PageRequest(0, 10)))
        .thenReturn(new PageResult<>(List.of(reading), 0, 10, 1));

    var response =
        new RecommendPlatformReadingsEndpoint(port, new RecommendPlatformReadingsSoapMapper())
            .recommendPlatformReadings(request);

    assertThat(RecommendPlatformReadingsRequest.class.getMethods())
        .noneMatch(method -> method.getName().equals("getUserId"));
    assertThat(response.getReadings())
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.getEditorialLevel().value()).isEqualTo("B1");
              assertThat(item.getExplicitNewWords()).isEqualTo(1);
              assertThat(item.getUnclassifiedWords()).isEqualTo(1);
              assertThat(item.getVocabularyFitPercentage()).isEqualByComparingTo("56.00");
              assertThat(item.getClassificationConfidencePercentage())
                  .isEqualByComparingTo("80.00");
            });
    verify(port).recommendPlatformReadings(new PageRequest(0, 10));
  }
}
