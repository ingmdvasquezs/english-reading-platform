package com.soap.soap.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DocumentChunkerTest {
  private final DocumentChunker chunker = new DocumentChunker();

  @Test
  void keepsSmallContentInOneUnitAndIsDeterministic() {
    var shortSection = List.of(words(80, "short"));
    var longSection = IntStream.range(0, 24).mapToObj(i -> words(100, "p" + i)).toList();

    assertThat(chunker.chunk(shortSection, "fr-FR")).hasSize(1);
    assertThat(chunker.chunk(longSection, "es"))
        .isEqualTo(chunker.chunk(longSection, "es"))
        .hasSizeGreaterThan(1);
  }

  @Test
  void groupsParagraphsNearTargetWithoutCuttingSmallParagraphs() {
    var chunks =
        chunker.chunk(
            List.of(words(140, "first"), words(130, "second"), words(100, "third")), "en");

    assertThat(chunks).extracting(chunk -> chunk.wordCount()).containsExactly(140, 130, 100);
    assertThat(chunks.get(1).content()).isEqualTo(words(130, "second"));
  }

  @Test
  void startsANewUnitWhenTheNextParagraphClearlyExceedsMaximum() {
    var chunks = chunker.chunk(List.of(words(390, "first"), words(100, "second")), "en");

    assertThat(chunks).extracting(chunk -> chunk.wordCount()).containsExactly(260, 130, 100);
  }

  @Test
  void sentenceAndWordFallbackRespectHardLimitsForUnicode() {
    var giant =
        IntStream.range(0, 150)
            .mapToObj(i -> words(10, "café" + i) + ".")
            .collect(Collectors.joining(" "));

    assertThat(chunker.chunk(List.of(giant), "fr"))
        .hasSizeGreaterThan(1)
        .allMatch(
            chunk ->
                chunk.wordCount() <= DocumentChunker.HARD_WORDS
                    && chunk.content().getBytes(StandardCharsets.UTF_8).length <= 80 * 1024);
  }

  @Test
  void splitsAnExceptionalParagraphBySentencesAndPreservesOrder() {
    var giant =
        IntStream.range(0, 80)
            .mapToObj(index -> words(10, "sentence" + index) + ".")
            .collect(Collectors.joining(" "));

    var chunks = chunker.chunk(List.of(giant), "en");

    assertThat(chunks).hasSizeGreaterThan(1);
    assertThat(chunks).allMatch(chunk -> chunk.wordCount() <= DocumentChunker.HARD_WORDS);
    assertThat(chunks.stream().map(chunk -> chunk.content()).collect(Collectors.joining(" ")))
        .isEqualTo(giant);
  }

  @Test
  void wordFallbackNeverCutsAWord() {
    var giant = words(700, "token");
    var chunks = chunker.chunk(List.of(giant), "en");

    assertThat(chunks).allMatch(chunk -> chunk.wordCount() <= DocumentChunker.HARD_WORDS);
    assertThat(chunks.stream().map(chunk -> chunk.content()).collect(Collectors.joining(" ")))
        .isEqualTo(giant);
  }

  @Test
  void keepsAHeadingAndSpeakerWithTheirFollowingContent() {
    var chunks =
        chunker.chunk(
            List.of(
                words(250, "prior"),
                "Scene I",
                "ORLANDO",
                words(120, "dialogue"),
                words(250, "following")),
            "en");

    assertThat(chunks).hasSizeGreaterThan(2);
    assertThat(chunks.get(1).content())
        .startsWith("Scene I\n\nORLANDO\n\n")
        .contains(words(120, "dialogue"));
  }

  @Test
  void allowsContextToReachHardMaximumInsteadOfIsolatingIt() {
    var chunks = chunker.chunk(List.of("CHAPTER ONE", words(250, "content")), "en");

    assertThat(chunks)
        .singleElement()
        .satisfies(
            chunk -> {
              assertThat(chunk.content()).startsWith("CHAPTER ONE\n\ncontent0");
              assertThat(chunk.wordCount()).isEqualTo(252);
            });
  }

  @Test
  void preservesParagraphPunctuationAndBoundariesAcrossV4Chunks() {
    var first = "Don’t stop—ever. \"We won't,\" she replied.";
    var second = words(210, "middle") + ".";
    var third = "A final paragraph—with punctuation.";

    var chunks = chunker.chunk(List.of(first, second, third), "en");

    assertThat(chunks.stream().map(chunk -> chunk.content()).collect(Collectors.joining("\n\n")))
        .isEqualTo(String.join("\n\n", first, second, third));
    assertThat(chunks).allMatch(chunk -> chunk.wordCount() <= DocumentChunker.HARD_WORDS);
  }

  @Test
  void ignoresEmptyAndNonTextualBlocks() {
    assertThat(chunker.chunk(java.util.Arrays.asList(null, " ", "Readable text."), "en"))
        .singleElement()
        .satisfies(
            chunk -> {
              assertThat(chunk.content()).isEqualTo("Readable text.");
              assertThat(chunk.wordCount()).isEqualTo(2);
            });
  }

  @Test
  void chunksEachSectionIndependentlyAtTheCallerBoundary() {
    var first = chunker.chunk(List.of(words(800, "first")), "en");
    var second = chunker.chunk(List.of(words(800, "second")), "en");

    assertThat(first).allMatch(chunk -> !chunk.content().contains("second"));
    assertThat(second).allMatch(chunk -> !chunk.content().contains("first"));
  }

  private String words(int count, String prefix) {
    return IntStream.range(0, count)
        .mapToObj(index -> prefix + index)
        .collect(Collectors.joining(" "));
  }
}
