package com.soap.soap.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReadingTest {
  private final User owner = new User(UUID.randomUUID(), "Ada", "ada@example.com");

  @Test
  void userReadingRequiresAnOwnerAndHasNoPlatformMetadata() {
    assertThatThrownBy(
            () ->
                new Reading(
                    null,
                    null,
                    "Title",
                    "Content",
                    "en",
                    null,
                    ReadingOrigin.USER,
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("owner");
    assertThatThrownBy(
            () ->
                new Reading(
                    null,
                    owner,
                    "Title",
                    "Content",
                    "en",
                    null,
                    ReadingOrigin.USER,
                    EditorialLevel.A1,
                    "Daily Life",
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("platform metadata");
    assertThatThrownBy(
            () ->
                new Reading(
                    null,
                    owner,
                    "Title",
                    "Content",
                    "en",
                    null,
                    ReadingOrigin.USER,
                    null,
                    null,
                    null,
                    EditorialStatus.PUBLISHED))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("editorial status");
  }

  @Test
  void platformReadingRequiresMetadataAndExplicitEditorialStatus() {
    assertThatThrownBy(
            () ->
                new Reading(
                    null,
                    owner,
                    "Title",
                    "Content",
                    "en",
                    null,
                    ReadingOrigin.PLATFORM,
                    EditorialLevel.A1,
                    "Daily Life",
                    EditorialStatus.PUBLISHED))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not have an owner");
    assertThatThrownBy(
            () ->
                new Reading(
                    null,
                    null,
                    "Title",
                    "Content",
                    "en",
                    null,
                    ReadingOrigin.PLATFORM,
                    null,
                    null,
                    null,
                    EditorialStatus.PUBLISHED))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("editorial metadata");
    assertThatThrownBy(
            () ->
                new Reading(
                    null,
                    null,
                    "Title",
                    "Content",
                    "en",
                    null,
                    ReadingOrigin.PLATFORM,
                    EditorialLevel.A1,
                    "Daily Life",
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("explicit editorial status");
  }

  @Test
  void platformIsAccessibleToAnyUserWhileUserReadingIsOwnerOnly() {
    var platform =
        new Reading(
            UUID.randomUUID(),
            null,
            "Platform",
            "Content",
            "en",
            LocalDateTime.now(),
            ReadingOrigin.PLATFORM,
            EditorialLevel.B1,
            "Science",
            EditorialStatus.PUBLISHED);
    var userReading = new Reading(null, owner, "User", "Content", "en", null);

    assertThat(platform.isAccessibleBy(UUID.randomUUID())).isTrue();
    assertThat(userReading.isAccessibleBy(owner.id())).isTrue();
    assertThat(userReading.isAccessibleBy(UUID.randomUUID())).isFalse();
  }

  @Test
  void coverKeyIsOptionalVisualMetadataForBothOrigins() {
    var platform =
        new Reading(
            UUID.randomUUID(),
            null,
            "Platform",
            "Content",
            "en",
            LocalDateTime.now(),
            ReadingOrigin.PLATFORM,
            EditorialLevel.B1,
            "Science",
            "platform-cover",
            EditorialStatus.PUBLISHED);
    var userReading = new Reading(null, owner, "User", "Content", "en", null);

    assertThat(platform.coverKey()).isEqualTo("platform-cover");
    assertThat(userReading.coverKey()).isNull();
  }
}
