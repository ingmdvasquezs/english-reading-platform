package com.soap.soap.spike.epub;

import com.soap.soap.spike.epub.EpubSpikeModel.Block;
import com.soap.soap.spike.epub.EpubSpikeModel.Chunk;
import java.nio.charset.StandardCharsets;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

final class PrototypeChunker {

  static final int VERSION = 1;
  private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{M}]+(?:['’][\\p{L}\\p{M}]+)*");

  private final int targetWords;
  private final int minimumWords;
  private final int maximumWords;
  private final int hardWords;
  private final int hardBytes;

  PrototypeChunker() {
    this(700, 450, 900, 1_200, 80 * 1024);
  }

  PrototypeChunker(
      int targetWords, int minimumWords, int maximumWords, int hardWords, int hardBytes) {
    this.targetWords = targetWords;
    this.minimumWords = minimumWords;
    this.maximumWords = maximumWords;
    this.hardWords = hardWords;
    this.hardBytes = hardBytes;
  }

  List<Chunk> chunk(List<Block> source, Locale locale) {
    var blocks = source.stream().flatMap(block -> splitOversized(block, locale).stream()).toList();
    var chunks = new ArrayList<Chunk>();
    var current = new ArrayList<Block>();
    for (var block : blocks) {
      var candidate = new ArrayList<>(current);
      candidate.add(block);
      if (!current.isEmpty() && exceedsSoftMaximum(candidate)) {
        addChunk(chunks, current);
        current = new ArrayList<>();
      }
      current.add(block);
      if (wordCount(current) >= targetWords && wordCount(current) >= minimumWords) {
        addChunk(chunks, current);
        current = new ArrayList<>();
      }
    }
    if (!current.isEmpty()) {
      if (!chunks.isEmpty() && wordCount(current) < minimumWords) {
        var previous = chunks.removeLast();
        var merged = new ArrayList<>(previous.blocks());
        merged.addAll(current);
        if (!exceedsHardLimit(merged)) addChunk(chunks, merged);
        else {
          chunks.add(previous);
          addChunk(chunks, current);
        }
      } else addChunk(chunks, current);
    }
    return List.copyOf(chunks);
  }

  private List<Block> splitOversized(Block block, Locale locale) {
    if (!exceedsHardLimit(List.of(block))) return List.of(block);
    var sentences = sentences(block.text(), locale);
    var result = new ArrayList<Block>();
    var current = new StringBuilder();
    for (var sentence : sentences) {
      if (!current.isEmpty() && exceedsHardLimit(current + " " + sentence)) {
        result.add(new Block(block.type(), current.toString()));
        current.setLength(0);
      }
      if (exceedsHardLimit(sentence)) {
        if (!current.isEmpty()) {
          result.add(new Block(block.type(), current.toString()));
          current.setLength(0);
        }
        hardSplit(sentence, block.type(), result);
      } else {
        if (!current.isEmpty()) current.append(' ');
        current.append(sentence);
      }
    }
    if (!current.isEmpty()) result.add(new Block(block.type(), current.toString()));
    return result;
  }

  private void hardSplit(String text, Block.Type type, List<Block> output) {
    var words = text.strip().split("\\s+");
    var current = new StringBuilder();
    for (var word : words) {
      var candidate = current.isEmpty() ? word : current + " " + word;
      if (!current.isEmpty() && exceedsHardLimit(candidate)) {
        output.add(new Block(type, current.toString()));
        current.setLength(0);
      }
      if (!current.isEmpty()) current.append(' ');
      current.append(word);
    }
    if (!current.isEmpty()) output.add(new Block(type, current.toString()));
  }

  private List<String> sentences(String text, Locale locale) {
    var iterator = BreakIterator.getSentenceInstance(locale);
    iterator.setText(text);
    var result = new ArrayList<String>();
    for (int start = iterator.first(), end = iterator.next();
        end != BreakIterator.DONE;
        start = end, end = iterator.next()) {
      var sentence = text.substring(start, end).strip();
      if (!sentence.isEmpty()) result.add(sentence);
    }
    return result.isEmpty() ? List.of(text) : result;
  }

  private boolean exceedsSoftMaximum(List<Block> blocks) {
    return wordCount(blocks) > maximumWords || utf8Bytes(blocks) > hardBytes;
  }

  private boolean exceedsHardLimit(List<Block> blocks) {
    return wordCount(blocks) > hardWords || utf8Bytes(blocks) > hardBytes;
  }

  private boolean exceedsHardLimit(CharSequence text) {
    return wordCount(text.toString()) > hardWords
        || text.toString().getBytes(StandardCharsets.UTF_8).length > hardBytes;
  }

  private void addChunk(List<Chunk> chunks, List<Block> blocks) {
    chunks.add(new Chunk(chunks.size() + 1, blocks, wordCount(blocks), utf8Bytes(blocks)));
  }

  private int wordCount(List<Block> blocks) {
    return blocks.stream().mapToInt(block -> wordCount(block.text())).sum();
  }

  private int wordCount(String text) {
    var matcher = WORD.matcher(text);
    var count = 0;
    while (matcher.find()) count++;
    return count;
  }

  private int utf8Bytes(List<Block> blocks) {
    return blocks.stream()
        .map(Block::text)
        .reduce((a, b) -> a + "\n\n" + b)
        .orElse("")
        .getBytes(StandardCharsets.UTF_8)
        .length;
  }
}
