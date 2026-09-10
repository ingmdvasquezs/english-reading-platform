package com.soap.soap.infrastructure.http.adapter;

import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class MerriamAudioUrlBuilder {
  private static final String BASE_URL = "https://media.merriam-webster.com/audio/prons/en/us/mp3/";
  private static final Pattern SAFE_AUDIO = Pattern.compile("[A-Za-z0-9_-]+");

  public String build(String audio) {
    if (audio == null || audio.isBlank() || !SAFE_AUDIO.matcher(audio).matches()) {
      return null;
    }
    var normalized = audio.toLowerCase(Locale.ROOT);
    var directory =
        normalized.startsWith("bix")
            ? "bix"
            : normalized.startsWith("gg")
                ? "gg"
                : Character.isLetter(normalized.charAt(0))
                    ? String.valueOf(normalized.charAt(0))
                    : "number";
    return BASE_URL + directory + "/" + audio + ".mp3";
  }
}
