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
  public static final int VERSION = 4;
  static final int TARGET_WORDS = 160;
  static final int MINIMUM_WORDS = 100;
  static final int MAXIMUM_WORDS = 220;
  static final int HARD_WORDS = 260;
  static final int HARD_BYTES = 80 * 1024;
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
    for (var block : blocks) {
      var candidate = new ArrayList<>(current);
      candidate.add(block);
      if (!current.isEmpty() && exceedsSoft(candidate)) {
        var context = trailingContext(current);
        var contentSize = current.size() - context.size();
        if (contentSize > 0) {
          add(chunks, current.subList(0, contentSize));
          current = new ArrayList<>(context);
        } else if (exceedsHard(candidate)) {
          add(chunks, current);
          current = new ArrayList<>();
        }
      }
      current.add(block);
      if (words(current) >= targetWords && !isContext(block)) {
        add(chunks, current);
        current = new ArrayList<>();
      }
    }
    if (!current.isEmpty()) {
      if (!chunks.isEmpty() && words(current) < minimumWords) {
        var prior = chunks.removeLast();
        var merged = new ArrayList<>(List.of(prior.content()));
        merged.addAll(current);
        if (!exceedsSoft(merged)) add(chunks, merged);
        else {
          chunks.add(prior);
          add(chunks, current);
        }
      } else add(chunks, current);
    }
    return List.copyOf(chunks);
  }

  public int wordCount(String value) {
    var matcher = WORD.matcher(value);
    var count = 0;
    while (matcher.find()) count++;
    return count;
  }

  private List<String> splitOversized(String block, Locale locale) {
    if (!exceedsHard(List.of(block))) return List.of(block);
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

  private boolean exceedsSoft(List<String> blocks) {
    return words(blocks) > maximumWords || bytes(blocks) > HARD_BYTES;
  }

  private boolean exceedsHard(List<String> blocks) {
    return words(blocks) > hardWords || bytes(blocks) > HARD_BYTES;
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
