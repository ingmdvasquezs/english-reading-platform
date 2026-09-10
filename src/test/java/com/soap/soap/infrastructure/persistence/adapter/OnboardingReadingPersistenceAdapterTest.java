package com.soap.soap.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soap.soap.application.service.TextWordProcessor;
import com.soap.soap.domain.model.OnboardingReading;
import com.soap.soap.infrastructure.persistence.entity.OnboardingReadingEntity;
import com.soap.soap.infrastructure.persistence.mapper.OnboardingReadingEntityMapper;
import com.soap.soap.infrastructure.persistence.repository.JpaOnboardingReadingRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OnboardingReadingPersistenceAdapterTest {
  @Mock private JpaOnboardingReadingRepository repository;
  @Mock private OnboardingReadingEntityMapper mapper;
  private final TextWordProcessor processor = new TextWordProcessor();

  @Test
  void loadsAndTokenizesTheLatestActiveReading() {
    var entity = new OnboardingReadingEntity();
    var reading =
        new OnboardingReading(
            7L, "A title", "Hello bright world.\n\nHello again.", true, 3, LocalDateTime.now());
    when(repository.findFirstByActiveTrueOrderByVersionDescCreatedAtDescIdDesc())
        .thenReturn(Optional.of(entity));
    when(mapper.toDomain(entity)).thenReturn(reading);
    var adapter = new OnboardingReadingPersistenceAdapter(repository, mapper, processor);

    var result = adapter.load();

    assertThat(result.testId()).isEqualTo("onboarding-reading-7-v3");
    assertThat(result.text()).isEqualTo(reading.content()).contains("\n\n");
    assertThat(result.selectableWords()).containsExactly("hello", "bright", "world", "again");
    verify(repository).findFirstByActiveTrueOrderByVersionDescCreatedAtDescIdDesc();
  }

  @Test
  void failsClearlyWhenThereIsNoActiveReading() {
    when(repository.findFirstByActiveTrueOrderByVersionDescCreatedAtDescIdDesc())
        .thenReturn(Optional.empty());
    var adapter = new OnboardingReadingPersistenceAdapter(repository, mapper, processor);

    assertThatThrownBy(adapter::load)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("No active onboarding reading configured");
  }
}
