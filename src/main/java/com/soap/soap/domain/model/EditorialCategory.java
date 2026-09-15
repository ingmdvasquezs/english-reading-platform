package com.soap.soap.domain.model;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The 8 canonical thematic categories for platform editorial content. Persistent labels match
 * approved display strings exactly.
 */
public enum EditorialCategory {
  DAILY_LIFE_AND_RELATIONSHIPS("Daily Life & Relationships"),
  WORK_AND_SOCIETY("Work & Society"),
  SCIENCE_AND_TECHNOLOGY("Science & Technology"),
  NATURE_AND_ENVIRONMENT("Nature & Environment"),
  MYSTERY_AND_EXPLORATION("Mystery & Exploration"),
  TRAVEL_AND_PLACES("Travel & Places"),
  CULTURE_ARTS_AND_FICTION("Culture, Arts & Fiction"),
  HISTORY_AND_MEMORY("History & Memory");

  private final String displayName;

  EditorialCategory(String displayName) {
    this.displayName = displayName;
  }

  public String displayName() {
    return displayName;
  }

  private static final Map<String, EditorialCategory> BY_DISPLAY_NAME =
      Arrays.stream(values())
          .collect(
              Collectors.toUnmodifiableMap(
                  c -> c.displayName.toLowerCase(java.util.Locale.ROOT), Function.identity()));

  public static EditorialCategory fromDisplayName(String name) {
    if (name == null || name.isBlank()) {
      return null;
    }
    EditorialCategory category =
        BY_DISPLAY_NAME.get(name.trim().toLowerCase(java.util.Locale.ROOT));
    if (category == null) {
      throw new IllegalArgumentException("Unknown editorial category: " + name);
    }
    return category;
  }
}
