package com.soap.soap.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.infrastructure.http.adapter.MerriamAudioUrlBuilder;
import com.soap.soap.infrastructure.http.adapter.MerriamMarkupSanitizer;
import org.junit.jupiter.api.Test;

class MerriamUtilitiesTest {
  @Test
  void sanitizerCleansFormattingAndDropsAmbiguousReferences() {
    var s = new MerriamMarkupSanitizer();
    assertThat(s.sanitize("{bc}a round {it}fruit{/it}")).isEqualTo("a round fruit");
    assertThat(s.sanitize("before {dx}reference{/dx} after")).isEqualTo("before after");
    assertThat(s.sanitize("see {dxt|apple|apple} now")).isEqualTo("see now");
  }

  @Test
  void audioBuilderImplementsOfficialDirectories() {
    var b = new MerriamAudioUrlBuilder();
    assertThat(b.build("bixword")).contains("/bix/bixword.mp3");
    assertThat(b.build("ggword")).contains("/gg/ggword.mp3");
    assertThat(b.build("_word")).contains("/number/_word.mp3");
    assertThat(b.build("3word")).contains("/number/3word.mp3");
    assertThat(b.build("apple001")).contains("/a/apple001.mp3");
    assertThat(b.build("../secret")).isNull();
  }
}
