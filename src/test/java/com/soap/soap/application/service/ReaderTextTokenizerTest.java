package com.soap.soap.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.application.model.ReaderTokenType;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ReaderTextTokenizerTest {
  private final ReaderTextTokenizer tokenizer = new ReaderTextTokenizer(new TextWordProcessor());

  @Test
  void preservesEveryCharacterAndClassifiesVisualTokens() {
    var content = "Hello,  ‘world’!\r\nDon't stop... #42";

    var tokens = tokenizer.tokenize(content);

    assertThat(tokens.stream().map(token -> token.value()).collect(Collectors.joining()))
        .isEqualTo(content);
    assertThat(tokens)
        .anySatisfy(
            token -> {
              assertThat(token.value()).isEqualTo("Hello");
              assertThat(token.normalizedValue()).isEqualTo("hello");
              assertThat(token.type()).isEqualTo(ReaderTokenType.WORD);
            })
        .anySatisfy(
            token -> {
              assertThat(token.value()).isEqualTo("\r\n");
              assertThat(token.type()).isEqualTo(ReaderTokenType.WHITESPACE);
              assertThat(token.normalizedValue()).isNull();
              assertThat(token.status()).isNull();
            })
        .anySatisfy(
            token -> {
              assertThat(token.value()).contains(",");
              assertThat(token.type()).isEqualTo(ReaderTokenType.PUNCTUATION);
              assertThat(token.status()).isNull();
            })
        .anySatisfy(
            token -> {
              assertThat(token.value()).isEqualTo("Don't");
              assertThat(token.normalizedValue()).isEqualTo("don't");
            });
  }

  @Test
  void supportsEmptyAndPunctuationOnlyContent() {
    assertThat(tokenizer.tokenize("")).isEmpty();
    assertThat(tokenizer.tokenize("...!?"))
        .singleElement()
        .satisfies(token -> assertThat(token.type()).isEqualTo(ReaderTokenType.PUNCTUATION));
  }
}
