package com.soap.soap.application.service;

import com.soap.soap.application.model.ReaderToken;
import com.soap.soap.application.model.ReaderTokenType;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReaderTextTokenizer {
  private final TextWordProcessor wordProcessor;

  public List<ReaderToken> tokenize(String text) {
    var tokens = new ArrayList<ReaderToken>();
    var matcher = wordProcessor.wordPattern().matcher(text);
    var cursor = 0;
    while (matcher.find()) {
      addNonWordTokens(text.substring(cursor, matcher.start()), tokens);
      var value = matcher.group();
      tokens.add(
          new ReaderToken(value, wordProcessor.normalize(value), ReaderTokenType.WORD, null));
      cursor = matcher.end();
    }
    addNonWordTokens(text.substring(cursor), tokens);
    return List.copyOf(tokens);
  }

  private void addNonWordTokens(String value, List<ReaderToken> tokens) {
    if (value.isEmpty()) {
      return;
    }
    var start = 0;
    var whitespace = Character.isWhitespace(value.charAt(0));
    for (var index = 1; index < value.length(); index++) {
      var currentWhitespace = Character.isWhitespace(value.charAt(index));
      if (currentWhitespace != whitespace) {
        addNonWordToken(value.substring(start, index), whitespace, tokens);
        start = index;
        whitespace = currentWhitespace;
      }
    }
    addNonWordToken(value.substring(start), whitespace, tokens);
  }

  private void addNonWordToken(String value, boolean whitespace, List<ReaderToken> tokens) {
    tokens.add(
        new ReaderToken(
            value,
            null,
            whitespace ? ReaderTokenType.WHITESPACE : ReaderTokenType.PUNCTUATION,
            null));
  }
}
