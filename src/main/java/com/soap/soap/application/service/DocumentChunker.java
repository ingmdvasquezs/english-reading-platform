package com.soap.soap.application.service;

import com.soap.soap.application.model.DocumentChunk;
import java.nio.charset.StandardCharsets;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class DocumentChunker {
  public static final int VERSION = 5;

  static final int TARGET_WORDS = 160;
  static final int MINIMUM_WORDS = 100;
  static final int MAXIMUM_WORDS = 220;
  static final int HARD_WORDS = 260;
  static final int HARD_BYTES = 80 * 1024;

  static final double DENSITY_THRESHOLD_WORDS_PER_BLOCK = 15.0;
  static final int DENSE_MIN_BLOCKS_TO_CLASSIFY = 6;
  static final int DENSE_TARGET_WORDS = 110;
  static final int DENSE_MINIMUM_WORDS = 60;
  static final int DENSE_SOFT_MAX_WORDS = 150;
  static final int DENSE_MAX_BLOCKS = 20;
  static final int DENSE_HARD_MAX_BLOCKS = 25;
  static final int PROSE_HARD_MAX_BLOCKS = 50;

  private final int targetWords;
  private final int minimumWords;
  private final int maximumWords;
  private final int hardWords;
  private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{M}]+(?:['’][\\p{L}\\p{M}]+)*");
  private static final Pattern CONTEXT_BLOCK =
      Pattern.compile(
          "(?iu)^(?:act|book|chapter|part|scene|section)\\b.*|^\\[.*]|^[\\p{Lu}\\p{M} .'-]+$");

  public DocumentChunker() {
    this(TARGET_WORDS, MINIMUM_WORDS, MAXIMUM_WORDS, HARD_WORDS);
  }

  DocumentChunker(int targetWords, int minimumWords, int maximumWords, int hardWords) {
    this.targetWords = targetWords;
    this.minimumWords = minimumWords;
    this.maximumWords = maximumWords;
    this.hardWords = hardWords;
  }

  public List<DocumentChunk> chunk(List<String> source, String language) {
    var locale = Locale.forLanguageTag(language);
    var blocks =
        source.stream()
            .filter(java.util.Objects::nonNull)
            .map(String::strip)
            .filter(block -> !block.isEmpty())
            .flatMap(block -> splitOversized(block, locale).stream())
            .toList();
    var chunks = new ArrayList<DocumentChunk>();
    var current = new ArrayList<String>();
    var denseMode = false;

    for (var block : blocks) {
      var candidate = new ArrayList<>(current);
      candidate.add(block);
      int candidateWords = words(candidate);
      int candidateBlocks = candidate.size();

      if (isDense(candidateWords, candidateBlocks)) {
        denseMode = true;
      }

      if (!current.isEmpty() && exceedsSoft(candidate, denseMode)) {
        var context = trailingContext(current);
        var contentSize = current.size() - context.size();
        if (contentSize > 0) {
          add(chunks, current.subList(0, contentSize));
          current = new ArrayList<>(context);
          denseMode = isDense(words(current), current.size());
        } else if (exceedsHard(candidate, denseMode)) {
          add(chunks, current);
          current = new ArrayList<>();
          denseMode = false;
        }
      }
      current.add(block);

      int currentWords = words(current);
      int currentBlocks = current.size();
      if (isDense(currentWords, currentBlocks)) {
        denseMode = true;
      }

      if (reachesTarget(currentWords, currentBlocks, denseMode, block)) {
        add(chunks, current);
        current = new ArrayList<>();
        denseMode = false;
      }
    }

    if (!current.isEmpty()) {
      int trailingWords = words(current);
      int trailingBlocks = current.size();
      boolean currentIsDense = denseMode || isDense(trailingWords, trailingBlocks);
      int effectiveMinWords = currentIsDense ? DENSE_MINIMUM_WORDS : minimumWords;

      if (!chunks.isEmpty() && trailingWords < effectiveMinWords) {
        var prior = chunks.removeLast();
        var priorBlocks = List.of(prior.content().split("\n\n", -1));
        var mergedBlocks = new ArrayList<>(priorBlocks);
        mergedBlocks.addAll(current);
        boolean mergedDense = isDense(words(mergedBlocks), mergedBlocks.size());

        if (!exceedsSoft(mergedBlocks, mergedDense) && !exceedsHard(mergedBlocks, mergedDense)) {
          add(chunks, mergedBlocks);
        } else {
          chunks.add(prior);
          add(chunks, current);
        }
      } else {
        add(chunks, current);
      }
    }
    return List.copyOf(chunks);
  }

  private boolean reachesTarget(
      int currentWords, int currentBlocks, boolean denseMode, String lastBlock) {
    if (denseMode) {
      if (currentBlocks >= DENSE_HARD_MAX_BLOCKS) {
        return true;
      }
      return (currentWords >= DENSE_TARGET_WORDS || currentBlocks >= DENSE_MAX_BLOCKS)
          && currentWords >= DENSE_MINIMUM_WORDS
          && !isContext(lastBlock);
    }
    return currentWords >= targetWords && !isContext(lastBlock);
  }

  private boolean isDense(int words, int blocks) {
    return blocks >= DENSE_MIN_BLOCKS_TO_CLASSIFY
        && ((double) words / blocks) < DENSITY_THRESHOLD_WORDS_PER_BLOCK;
  }

  private boolean exceedsSoft(List<String> blocks, boolean dense) {
    if (bytes(blocks) > HARD_BYTES) return true;
    int w = words(blocks);
    int b = blocks.size();
    if (dense) {
      if (w > DENSE_SOFT_MAX_WORDS) return true;
      if (b > DENSE_HARD_MAX_BLOCKS) return true;
      return b > DENSE_MAX_BLOCKS && w >= DENSE_MINIMUM_WORDS;
    }
    return w > maximumWords || b > PROSE_HARD_MAX_BLOCKS;
  }

  private boolean exceedsHard(List<String> blocks, boolean dense) {
    if (bytes(blocks) > HARD_BYTES) return true;
    if (words(blocks) > hardWords) return true;
    int b = blocks.size();
    if (b <= 1) return false;
    return dense ? b > DENSE_HARD_MAX_BLOCKS : b > PROSE_HARD_MAX_BLOCKS;
  }

  public int wordCount(String value) {
    var matcher = WORD.matcher(value);
    var count = 0;
    while (matcher.find()) count++;
    return count;
  }

  private List<String> splitOversized(String block, Locale locale) {
    if (!exceedsHard(List.of(block), false)) return List.of(block);
    var iterator = BreakIterator.getSentenceInstance(locale);
    iterator.setText(block);
    var sentences = new ArrayList<String>();
    for (int start = iterator.first(), end = iterator.next();
        end != BreakIterator.DONE;
        start = end, end = iterator.next()) {
      var sentence = block.substring(start, end).strip();
      if (!sentence.isEmpty()) sentences.add(sentence);
    }
    if (sentences.isEmpty()) sentences.add(block);
    var result = new ArrayList<String>();
    var current = new StringBuilder();
    for (var sentence : sentences) {
      if (!current.isEmpty() && exceedsHard(current + " " + sentence)) {
        result.add(current.toString());
        current.setLength(0);
      }
      if (exceedsHard(sentence)) {
        if (!current.isEmpty()) {
          result.add(current.toString());
          current.setLength(0);
        }
        hardSplit(sentence, result);
      } else {
        if (!current.isEmpty()) current.append(' ');
        current.append(sentence);
      }
    }
    if (!current.isEmpty()) result.add(current.toString());
    return result;
  }

  private void hardSplit(String value, List<String> output) {
    var current = new StringBuilder();
    for (var word : value.strip().split("\\s+")) {
      var candidate = current.isEmpty() ? word : current + " " + word;
      if (!current.isEmpty() && exceedsHard(candidate)) {
        output.add(current.toString());
        current.setLength(0);
      }
      if (!current.isEmpty()) current.append(' ');
      current.append(word);
    }
    if (!current.isEmpty()) output.add(current.toString());
  }

  private boolean exceedsHard(CharSequence value) {
    return wordCount(value.toString()) > hardWords
        || value.toString().getBytes(StandardCharsets.UTF_8).length > HARD_BYTES;
  }

  private List<String> trailingContext(List<String> blocks) {
    var start = blocks.size();
    while (start > 0 && isContext(blocks.get(start - 1))) start--;
    return List.copyOf(blocks.subList(start, blocks.size()));
  }

  private boolean isContext(String block) {
    return wordCount(block) <= 12 && CONTEXT_BLOCK.matcher(block.strip()).matches();
  }

  private int words(List<String> blocks) {
    return blocks.stream().mapToInt(this::wordCount).sum();
  }

  private int bytes(List<String> blocks) {
    return String.join("\n\n", blocks).getBytes(StandardCharsets.UTF_8).length;
  }

  private void add(List<DocumentChunk> chunks, List<String> blocks) {
    var content = String.join("\n\n", blocks);
    chunks.add(new DocumentChunk(content, wordCount(content)));
  }
}
