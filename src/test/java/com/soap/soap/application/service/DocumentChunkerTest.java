package com.soap.soap.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.application.model.DocumentChunk;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

  @Test
  void normalProseRetainsStandardTargetAndWordLimits() {
    // 4 paragraphs of 45 words each = 180 words, 4 blocks (45 words/block >> 15)
    var blocks = List.of(words(45, "a"), words(45, "b"), words(45, "c"), words(45, "d"));
    var chunks = chunker.chunk(blocks, "en");

    // Standard TARGET_WORDS is 160. First 4 blocks reach 180 and close as a single standard unit.
    assertThat(chunks).hasSize(1);
    assertThat(chunks.getFirst().wordCount()).isEqualTo(180);
  }

  @Test
  void denseTheatricalDialogueClosesEarlierAtDenseTargetOrMaxBlocks() {
    // 22 short dialogue blocks of 5 words each = 110 words, 22 blocks (5 words/block < 15)
    var blocks = IntStream.range(0, 22).mapToObj(i -> words(5, "line" + i)).toList();
    var chunks = chunker.chunk(blocks, "en");

    // Should close near dense target (110 words / 20 blocks), never creating a 40-60 block unit
    assertThat(chunks).hasSizeGreaterThan(1);
    assertThat(chunks)
        .allMatch(chunk -> blockCount(chunk) <= DocumentChunker.DENSE_HARD_MAX_BLOCKS);
  }

  @Test
  void ultrashortDialogueRespectsHardBlockLimitAndAvoidsMassiveUnits() {
    // 60 ultrashort blocks of 2 words = 120 words total
    var blocks = IntStream.range(0, 60).mapToObj(i -> "Yes, lord" + i + ".").toList();
    var chunks = chunker.chunk(blocks, "en");

    // Must partition into units of at most DENSE_HARD_MAX_BLOCKS (25), never 50-60 blocks
    assertThat(chunks).hasSizeGreaterThanOrEqualTo(3);
    assertThat(chunks)
        .allMatch(chunk -> blockCount(chunk) <= DocumentChunker.DENSE_HARD_MAX_BLOCKS);
  }

  @Test
  void longBlocksDoNotAccidentallyTriggerDenseProfile() {
    // 3 long paragraphs of 80 words = 240 words, 3 blocks (80 words/block)
    var blocks = List.of(words(80, "p1"), words(80, "p2"), words(80, "p3"));
    var chunks = chunker.chunk(blocks, "en");

    // Should behave as prose: 160 target
    assertThat(chunks).hasSize(2);
    assertThat(chunks.getFirst().wordCount()).isEqualTo(160);
    assertThat(chunks.get(1).wordCount()).isEqualTo(80);
  }

  @Test
  void boundaryExactThresholdAroundFifteenWordsPerBlock() {
    // 10 blocks of 16 words = 160 words (16.0 w/b >= 15.0) -> Prose mode
    var proseBlocks = IntStream.range(0, 10).mapToObj(i -> words(16, "prose" + i)).toList();
    var proseChunks = chunker.chunk(proseBlocks, "en");
    assertThat(proseChunks).hasSize(1);
    assertThat(proseChunks.getFirst().wordCount()).isEqualTo(160);

    // 16 blocks of 14 words = 224 words (14.0 w/b < 15.0) -> Dense mode (target 110)
    var denseBlocks = IntStream.range(0, 16).mapToObj(i -> words(14, "dense" + i)).toList();
    var denseChunks = chunker.chunk(denseBlocks, "en");
    // Closes at dense target (112 words, 8 blocks), leaving remaining 8 blocks (112 words) in
    // second chunk
    assertThat(denseChunks).hasSize(2);
    assertThat(denseChunks.getFirst().wordCount()).isEqualTo(112);
    assertThat(denseChunks.get(1).wordCount()).isEqualTo(112);
  }

  @Test
  void denseHardMaxBlocksForcesCloseEvenWhenBelowDenseMinimumWords() {
    // 25 blocks of 2 words each = 50 words (< DENSE_MINIMUM_WORDS of 60)
    var blocks =
        new ArrayList<>(IntStream.range(0, 25).mapToObj(i -> "No, sir" + i + ".").toList());
    // Add 5 more blocks
    blocks.addAll(IntStream.range(25, 30).mapToObj(i -> "Why so" + i + "?").toList());

    var chunks = chunker.chunk(blocks, "en");

    // First chunk MUST close at exactly 25 blocks because DENSE_HARD_MAX_BLOCKS is hard!
    assertThat(chunks).hasSize(2);
    assertThat(blockCount(chunks.getFirst())).isEqualTo(DocumentChunker.DENSE_HARD_MAX_BLOCKS);
    assertThat(chunks.getFirst().wordCount()).isLessThan(DocumentChunker.DENSE_MINIMUM_WORDS);
  }

  @Test
  void denseModeRemainsMonotonicAndDoesNotOscillateBeforeFlush() {
    // Starts with 1 long block of 70 words, then 10 short dialogue blocks of 3 words (11 blocks,
    // 100 words, 9.09 w/b < 15)
    var blocks = new ArrayList<String>();
    blocks.add(words(70, "monologue"));
    for (int i = 0; i < 10; i++) {
      blocks.add(words(3, "short" + i));
    }
    // Now add another block of 15 words: candidate is 115 words.
    blocks.add(words(15, "reply"));
    // Add more blocks to verify closure at dense target (115 words)
    blocks.add(words(100, "subsequent"));

    var chunks = chunker.chunk(blocks, "en");

    // The first chunk must have latched denseMode, closing around dense target (115 words) rather
    // than prose target (160)
    assertThat(chunks.getFirst().wordCount()).isEqualTo(115);
    assertThat(blockCount(chunks.getFirst())).isEqualTo(12);
  }

  @Test
  void trailingMergeStrictlyRejectsWhenMergedBlocksExceedBlockLimit() {
    // Chunk A has 20 dialogue blocks of 5 words = 100 words
    var blocks = new ArrayList<>(IntStream.range(0, 20).mapToObj(i -> words(5, "a" + i)).toList());
    // Chunk B has 7 dialogue blocks of 4 words = 28 words (< 60 words)
    blocks.addAll(IntStream.range(0, 7).mapToObj(i -> words(4, "b" + i)).toList());

    var chunks = chunker.chunk(blocks, "en");

    // Combined blocks = 27 > DENSE_HARD_MAX_BLOCKS (25). Merging MUST be rejected!
    assertThat(chunks).hasSize(2);
    assertThat(blockCount(chunks.getFirst())).isEqualTo(20);
    assertThat(blockCount(chunks.get(1))).isEqualTo(7);
  }

  @Test
  void preservesAllLogicalContentWithoutLossAcrossAdaptiveChunks() {
    var blocks = new ArrayList<String>();
    blocks.add("ACT I");
    blocks.add("SCENE I");
    blocks.addAll(
        IntStream.range(0, 40).mapToObj(i -> "Speaker dialogue line number " + i).toList());
    blocks.add(words(150, "prose"));

    var chunks = chunker.chunk(blocks, "en");

    var reconstructed =
        chunks.stream().map(DocumentChunk::content).collect(Collectors.joining("\n\n"));
    assertThat(reconstructed).isEqualTo(String.join("\n\n", blocks));
    assertThat(chunks)
        .allMatch(chunk -> blockCount(chunk) <= DocumentChunker.DENSE_HARD_MAX_BLOCKS);
  }

  private int blockCount(DocumentChunk chunk) {
    return chunk.content().split("\n\n", -1).length;
  }

  private String words(int count, String prefix) {
    return IntStream.range(0, count)
        .mapToObj(index -> prefix + index)
        .collect(Collectors.joining(" "));
  }
}
