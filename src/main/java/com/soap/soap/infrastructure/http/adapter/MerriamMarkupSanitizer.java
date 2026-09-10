package com.soap.soap.infrastructure.http.adapter;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class MerriamMarkupSanitizer {
  private static final Pattern DIRECTIONAL_BLOCK =
      Pattern.compile("\\{(?:dx|dx_def|dx_ety|ma)}.*?\\{/(?:dx|dx_def|dx_ety|ma)}");
  private static final Pattern COMPLEX_REFERENCE =
      Pattern.compile("\\{(?:dxt|sx|a_link|d_link|et_link|i_link|mat)\\|[^}]*}");
  private static final Pattern SIMPLE_TOKEN = Pattern.compile("\\{/?[a-zA-Z0-9_]+}");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  public String sanitize(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    var cleaned = DIRECTIONAL_BLOCK.matcher(value).replaceAll(" ");
    cleaned = COMPLEX_REFERENCE.matcher(cleaned).replaceAll(" ");
    cleaned = SIMPLE_TOKEN.matcher(cleaned).replaceAll(" ");
    cleaned = WHITESPACE.matcher(cleaned).replaceAll(" ").trim();
    return cleaned.isBlank() ? null : cleaned;
  }
}
