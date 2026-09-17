package com.soap.soap.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.domain.model.ReadingProgressStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReadingProgressCalculatorTest {

  private final ReadingProgressCalculator calculator = new ReadingProgressCalculator();

  @Test
  @DisplayName("A. IN_PROGRESS calculates expected real positional percentage")
  void inProgressCalculatesExpectedPercentage() {
    // Part 1 of 3 -> 33%
    assertThat(calculator.calculate(ReadingProgressStatus.IN_PROGRESS, 1, 1, 3)).isEqualTo(33);
    // Part 2 of 4 -> 50%
    assertThat(calculator.calculate(ReadingProgressStatus.IN_PROGRESS, 2, 1, 4)).isEqualTo(50);
  }

  @Test
  @DisplayName("B. Another reading produces a distinct non-constant percentage")
  void distinctReadingProducesDifferentPercentage() {
    // Part 1 of 4 -> 25%
    assertThat(calculator.calculate(ReadingProgressStatus.IN_PROGRESS, 1, 1, 4)).isEqualTo(25);
    // Part 2 of 3 -> 67%
    assertThat(calculator.calculate(ReadingProgressStatus.IN_PROGRESS, 2, 1, 3)).isEqualTo(67);
  }

  @Test
  @DisplayName("C. Started reading without position returns null")
  void startedOnlyReturnsNull() {
    assertThat(calculator.calculate(ReadingProgressStatus.IN_PROGRESS, null, null, 3)).isNull();
    assertThat(calculator.calculate(ReadingProgressStatus.IN_PROGRESS, null, 1, 3)).isNull();
    assertThat(calculator.calculate(ReadingProgressStatus.IN_PROGRESS, 1, null, 3)).isNull();
  }

  @Test
  @DisplayName("D. IN_PROGRESS at final part is strictly capped at 99 (never 100)")
  void inProgressAtFinalPartCappedAt99() {
    // 3 of 3 gives 100% mathematically, but IN_PROGRESS must be <= 99
    assertThat(calculator.calculate(ReadingProgressStatus.IN_PROGRESS, 3, 1, 3)).isEqualTo(99);
    // 1 of 1 gives 100% mathematically, but IN_PROGRESS must be <= 99
    assertThat(calculator.calculate(ReadingProgressStatus.IN_PROGRESS, 1, 1, 1)).isEqualTo(99);
    // 4 of 4 gives 100% mathematically, but IN_PROGRESS must be <= 99
    assertThat(calculator.calculate(ReadingProgressStatus.IN_PROGRESS, 4, 1, 4)).isEqualTo(99);
  }

  @Test
  @DisplayName("E. COMPLETED returns exactly 100")
  void completedReturns100() {
    assertThat(calculator.calculate(ReadingProgressStatus.COMPLETED, 3, 1, 3)).isEqualTo(100);
    assertThat(calculator.calculate(ReadingProgressStatus.COMPLETED, 1, 1, 4)).isEqualTo(100);
    assertThat(calculator.calculate(ReadingProgressStatus.COMPLETED, 2, 1, 2)).isEqualTo(100);
  }

  @Test
  @DisplayName("F. Unsupported pagination version returns null")
  void unsupportedPaginationVersionReturnsNull() {
    assertThat(calculator.calculate(ReadingProgressStatus.IN_PROGRESS, 2, 2, 4)).isNull();
    assertThat(calculator.calculate(ReadingProgressStatus.IN_PROGRESS, 2, 999, 4)).isNull();
    assertThat(calculator.calculate(ReadingProgressStatus.IN_PROGRESS, 2, 0, 4)).isNull();
    assertThat(calculator.calculate(ReadingProgressStatus.IN_PROGRESS, 2, -1, 4)).isNull();
  }

  @ParameterizedTest
  @CsvSource({"0, 4", "-1, 4", "5, 4", "10, 4"})
  @DisplayName("G. Out of bounds ordinal returns null")
  void outOfBoundsOrdinalReturnsNull(int ordinal, int totalParts) {
    assertThat(calculator.calculate(ReadingProgressStatus.IN_PROGRESS, ordinal, 1, totalParts))
        .isNull();
  }

  @ParameterizedTest
  @CsvSource({"1, 0", "1, -1"})
  @DisplayName("H. Invalid total parts returns null")
  void invalidTotalPartsReturnsNull(int ordinal, int totalParts) {
    assertThat(calculator.calculate(ReadingProgressStatus.IN_PROGRESS, ordinal, 1, totalParts))
        .isNull();
  }

  @Test
  @DisplayName("I. Null status returns null")
  void nullStatusReturnsNull() {
    assertThat(calculator.calculate(null, 1, 1, 3)).isNull();
  }
}
