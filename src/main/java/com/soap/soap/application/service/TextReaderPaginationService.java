package com.soap.soap.application.service;

import com.soap.soap.application.model.ReaderToken;
import com.soap.soap.application.model.ReaderTokenType;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TextReaderPaginationService {
  public static final int TEXT_PART_TARGET_WORDS = 160;
  public static final int TEXT_PART_MINIMUM_WORDS = 100;
  public static final int TEXT_PART_MAXIMUM_WORDS = 220;
  public static final int TEXT_PART_HARD_WORDS = 260;
  public static final int TEXT_PAGINATION_VERSION = 1;

  private static final Pattern DOUBLE_NEWLINE = Pattern.compile("(?:\\r?\\n)[\\t ]*(?:\\r?\\n)");
  private static final Pattern SENTENCE_PUNCTUATION =
      Pattern.compile("[.!?](?:[\"'\u2019\u201d)\\]])*$");

  private final ReaderTextTokenizer tokenizer;

  public Integer totalParts(String content, Integer paginationVersion) {
    if (content == null || content.isBlank() || paginationVersion == null) {
      return null;
    }
    if (paginationVersion != TEXT_PAGINATION_VERSION) {
      return null;
    }
    var tokens = tokenizer.tokenize(content);
    return calculateTotalPartsV1(tokens);
  }

  public int calculateTotalPartsV1(List<ReaderToken> tokens) {
    if (tokens == null || tokens.isEmpty()) {
      return 0;
    }
    int totalWords = countWords(tokens, 0, tokens.size());
    if (totalWords == 0) {
      return 0;
    }
    if (totalWords <= TEXT_PART_MAXIMUM_WORDS) {
      return 1;
    }

    var chunks = new ArrayList<TokenRange>();
    for (var paragraph : paragraphRanges(tokens)) {
      if (paragraph.wordCount > TEXT_PART_HARD_WORDS) {
        chunks.addAll(splitLongParagraph(tokens, paragraph));
      } else {
        chunks.add(paragraph);
      }
    }

    var grouped = new ArrayList<TokenRange>();
    TokenRange current = null;

    for (var chunk : chunks) {
      if (current == null) {
        current = new TokenRange(chunk.start, chunk.end, chunk.wordCount);
        continue;
      }

      int combinedWords = current.wordCount + chunk.wordCount;
      boolean combinedIsBetter =
          combinedWords <= TEXT_PART_MAXIMUM_WORDS
              && (current.wordCount < TEXT_PART_MINIMUM_WORDS
                  || Math.abs(TEXT_PART_TARGET_WORDS - combinedWords)
                      <= Math.abs(TEXT_PART_TARGET_WORDS - current.wordCount));

      if (combinedIsBetter) {
        current = new TokenRange(current.start, chunk.end, combinedWords);
      } else {
        grouped.add(current);
        current = new TokenRange(chunk.start, chunk.end, chunk.wordCount);
      }
    }
    if (current != null) {
      grouped.add(current);
    }

    mergeSmallFinalPart(grouped);
    return grouped.size();
  }

  private List<TokenRange> paragraphRanges(List<ReaderToken> tokens) {
    var ranges = new ArrayList<TokenRange>();
    int start = 0;
    for (int index = 0; index < tokens.size(); index++) {
      var token = tokens.get(index);
      if (token.type() == ReaderTokenType.WHITESPACE
          && DOUBLE_NEWLINE.matcher(token.value()).find()) {
        ranges.add(new TokenRange(start, index + 1, countWords(tokens, start, index + 1)));
        start = index + 1;
      }
    }
    if (start < tokens.size()) {
      ranges.add(new TokenRange(start, tokens.size(), countWords(tokens, start, tokens.size())));
    }
    return ranges;
  }

  private List<TokenRange> splitLongParagraph(List<ReaderToken> tokens, TokenRange paragraph) {
    var sentences = sentenceRanges(tokens, paragraph);
    var atomic = new ArrayList<TokenRange>();
    for (var sentence : sentences) {
      if (sentence.wordCount > TEXT_PART_HARD_WORDS) {
        atomic.addAll(splitRangeByWords(tokens, sentence));
      } else {
        atomic.add(sentence);
      }
    }

    var result = new ArrayList<TokenRange>();
    TokenRange current = null;

    for (var sentence : atomic) {
      if (current == null) {
        current = new TokenRange(sentence.start, sentence.end, sentence.wordCount);
        continue;
      }
      int combined = current.wordCount + sentence.wordCount;
      if (combined <= TEXT_PART_MAXIMUM_WORDS
          && (current.wordCount < TEXT_PART_MINIMUM_WORDS || combined <= TEXT_PART_TARGET_WORDS)) {
        current = new TokenRange(current.start, sentence.end, combined);
      } else {
        result.add(current);
        current = new TokenRange(sentence.start, sentence.end, sentence.wordCount);
      }
    }
    if (current != null) {
      result.add(current);
    }
    return result;
  }

  private List<TokenRange> sentenceRanges(List<ReaderToken> tokens, TokenRange source) {
    var ranges = new ArrayList<TokenRange>();
    int start = source.start;
    int index = source.start;
    while (index < source.end) {
      var token = tokens.get(index);
      if (token.type() == ReaderTokenType.PUNCTUATION
          && SENTENCE_PUNCTUATION.matcher(token.value()).find()) {
        int end = index + 1;
        while (end < source.end && tokens.get(end).type() == ReaderTokenType.WHITESPACE) {
          end++;
        }
        ranges.add(new TokenRange(start, end, countWords(tokens, start, end)));
        start = end;
        index = end;
      } else {
        index++;
      }
    }
    if (start < source.end) {
      ranges.add(new TokenRange(start, source.end, countWords(tokens, start, source.end)));
    }
    return ranges.isEmpty() ? List.of(source) : ranges;
  }

  private List<TokenRange> splitRangeByWords(List<ReaderToken> tokens, TokenRange source) {
    int partCount =
        Math.max(2, (int) Math.ceil((double) source.wordCount / (double) TEXT_PART_TARGET_WORDS));
    int baseWords = source.wordCount / partCount;
    int extra = source.wordCount % partCount;
    int[] targets = new int[partCount];
    for (int i = 0; i < partCount; i++) {
      targets[i] = baseWords + (i < extra ? 1 : 0);
    }
    var ranges = new ArrayList<TokenRange>();
    int start = source.start;
    int words = 0;
    int targetIndex = 0;

    for (int index = source.start;
        index < source.end && targetIndex < targets.length - 1;
        index++) {
      if (tokens.get(index).type() == ReaderTokenType.WORD) {
        words++;
      }
      if (words != targets[targetIndex]) {
        continue;
      }
      int end = index + 1;
      while (end < source.end && tokens.get(end).type() != ReaderTokenType.WORD) {
        end++;
      }
      ranges.add(new TokenRange(start, end, countWords(tokens, start, end)));
      start = end;
      words = 0;
      targetIndex++;
    }
    ranges.add(new TokenRange(start, source.end, countWords(tokens, start, source.end)));
    return ranges;
  }

  private void mergeSmallFinalPart(List<TokenRange> ranges) {
    if (ranges.size() < 2) {
      return;
    }
    var last = ranges.get(ranges.size() - 1);
    var previous = ranges.get(ranges.size() - 2);
    if (last.wordCount < TEXT_PART_MINIMUM_WORDS
        && previous.wordCount + last.wordCount <= TEXT_PART_MAXIMUM_WORDS) {
      ranges.set(
          ranges.size() - 2,
          new TokenRange(previous.start, last.end, previous.wordCount + last.wordCount));
      ranges.remove(ranges.size() - 1);
    }
  }

  private int countWords(List<ReaderToken> tokens, int start, int end) {
    int count = 0;
    for (int index = start; index < end; index++) {
      if (tokens.get(index).type() == ReaderTokenType.WORD) {
        count++;
      }
    }
    return count;
  }

  private record TokenRange(int start, int end, int wordCount) {}
}
