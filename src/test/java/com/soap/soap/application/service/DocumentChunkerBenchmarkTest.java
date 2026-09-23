package com.soap.soap.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.application.model.DocumentChunk;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class DocumentChunkerBenchmarkTest {

  private final DocumentChunker v5 = new DocumentChunker();
  private final LegacyV4Chunker v4 = new LegacyV4Chunker();

  @Test
  void benchmarkV4VsV5AcrossTheatreAndProse() throws Exception {
    java.sql.Connection conn;
    try {
      conn =
          DriverManager.getConnection(
              "jdbc:postgresql://localhost:5432/english_reading",
              "english_user",
              "english_password");
      try (var rs = conn.getMetaData().getTables(null, null, "document_units", null)) {
        if (!rs.next()) {
          org.junit.jupiter.api.Assumptions.abort(
              "Table document_units not present in local database");
          return;
        }
      }
    } catch (Exception e) {
      org.junit.jupiter.api.Assumptions.abort(
          "Local validation database not available at localhost:5432: " + e.getMessage());
      return;
    }

    try (conn) {
      var asYouLikeItId = UUID.fromString("a459fdf7-6c76-40d2-821b-0ad159bc420a");
      var soulsId = UUID.fromString("84d564ed-ff08-4245-b00f-bc01dae22f92");
      var bellJarId = UUID.fromString("1fc973e1-7218-451e-8f43-2649fdd9013d");

      var theatreSections = loadDocumentSections(conn, asYouLikeItId);
      var soulsSections = loadDocumentSections(conn, soulsId);
      var bellJarSections = loadDocumentSections(conn, bellJarId);

      if (theatreSections.isEmpty() || soulsSections.isEmpty() || bellJarSections.isEmpty()) {
        org.junit.jupiter.api.Assumptions.abort(
            "Benchmark test documents not found in local database");
        return;
      }

      var theatreV4 = runChunker(v4::chunk, theatreSections);
      var theatreV5 = runChunker(v5::chunk, theatreSections);

      var soulsV4 = runChunker(v4::chunk, soulsSections);
      var soulsV5 = runChunker(v5::chunk, soulsSections);

      var bellJarV4 = runChunker(v4::chunk, bellJarSections);
      var bellJarV5 = runChunker(v5::chunk, bellJarSections);

      System.out.println("=== THEATRE: AS YOU LIKE IT ===");
      printMetrics("V4 (Previous)", theatreV4);
      printMetrics("V5 (Adaptive)", theatreV5);

      System.out.println("\n=== PROSE: THE SOULS OF BLACK FOLK ===");
      printMetrics("V4 (Previous)", soulsV4);
      printMetrics("V5 (Adaptive)", soulsV5);

      System.out.println("\n=== PROSE: THE BELL JAR ===");
      printMetrics("V4 (Previous)", bellJarV4);
      printMetrics("V5 (Adaptive)", bellJarV5);

      // Invariants for Theatre (As You Like It)
      assertThat(theatreV5.maxBlocks).isLessThanOrEqualTo(DocumentChunker.DENSE_HARD_MAX_BLOCKS);
      assertThat(theatreV5.chunksOver25Blocks).isEqualTo(0);
      assertThat(theatreV4.maxBlocks).isGreaterThan(DocumentChunker.DENSE_HARD_MAX_BLOCKS);

      // Invariants for Prose: virtually unchanged
      double soulsUnitDelta =
          Math.abs((double) (soulsV5.totalUnits - soulsV4.totalUnits) / soulsV4.totalUnits);
      double bellJarUnitDelta =
          Math.abs((double) (bellJarV5.totalUnits - bellJarV4.totalUnits) / bellJarV4.totalUnits);
      assertThat(soulsUnitDelta).isLessThanOrEqualTo(0.05);
      assertThat(bellJarUnitDelta).isLessThanOrEqualTo(0.05);
    }
  }

  private List<List<String>> loadDocumentSections(java.sql.Connection conn, UUID documentId)
      throws Exception {
    var sql =
        "SELECT u.section_id, u.section_ordinal, u.content FROM document_units u "
            + "JOIN document_sections s ON u.section_id = s.id "
            + "WHERE s.document_id = ? "
            + "ORDER BY s.ordinal, u.section_ordinal";
    var result = new ArrayList<List<String>>();
    UUID currentSection = null;
    var currentBlocks = new ArrayList<String>();

    try (var ps = conn.prepareStatement(sql)) {
      ps.setObject(1, documentId);
      try (var rs = ps.executeQuery()) {
        while (rs.next()) {
          var secId = (UUID) rs.getObject("section_id");
          var content = rs.getString("content");
          if (currentSection != null && !currentSection.equals(secId)) {
            result.add(new ArrayList<>(currentBlocks));
            currentBlocks.clear();
          }
          currentSection = secId;
          for (var p : content.split("\n\n")) {
            var stripped = p.strip();
            if (!stripped.isEmpty()) currentBlocks.add(stripped);
          }
        }
      }
    }
    if (!currentBlocks.isEmpty()) {
      result.add(currentBlocks);
    }
    return result;
  }

  private Metrics runChunker(ChunkerAdapter chunker, List<List<String>> sections) {
    var allChunks = new ArrayList<DocumentChunk>();
    for (var section : sections) {
      allChunks.addAll(chunker.chunk(section, "en"));
    }

    var blockCounts = new ArrayList<Integer>();
    var wordCounts = new ArrayList<Integer>();
    var under50Words = 0;
    var over25Blocks = 0;
    var between20And25Blocks = 0;

    for (var chunk : allChunks) {
      var blocks = chunk.content().split("\n\n", -1).length;
      var words = chunk.wordCount();
      blockCounts.add(blocks);
      wordCounts.add(words);
      if (words < 50) under50Words++;
      if (blocks > 25) over25Blocks++;
      if (blocks >= 20 && blocks <= 25) between20And25Blocks++;
    }

    Collections.sort(blockCounts);

    var totalUnits = allChunks.size();
    var avgWords = wordCounts.stream().mapToInt(Integer::intValue).average().orElse(0.0);
    var avgBlocks = blockCounts.stream().mapToInt(Integer::intValue).average().orElse(0.0);
    var maxBlocks = blockCounts.isEmpty() ? 0 : blockCounts.getLast();
    var p50 = percentile(blockCounts, 0.50);
    var p90 = percentile(blockCounts, 0.90);
    var p95 = percentile(blockCounts, 0.95);
    var totalWords = wordCounts.stream().mapToInt(Integer::intValue).sum();
    var totalBlocks = blockCounts.stream().mapToInt(Integer::intValue).sum();
    var avgWordsPerBlock = totalBlocks == 0 ? 0.0 : (double) totalWords / totalBlocks;

    return new Metrics(
        totalUnits,
        avgWords,
        avgBlocks,
        p50,
        p90,
        p95,
        maxBlocks,
        under50Words,
        over25Blocks,
        between20And25Blocks,
        avgWordsPerBlock);
  }

  private double percentile(List<Integer> sorted, double pct) {
    if (sorted.isEmpty()) return 0;
    int index = (int) Math.ceil(pct * sorted.size()) - 1;
    return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
  }

  private void printMetrics(String label, Metrics m) {
    System.out.printf(
        "[%s] units=%d, avgWords=%.1f, avgBlocks=%.1f, p50=%.0f, p90=%.0f, p95=%.0f, maxBlocks=%d, <50w=%d, >25b=%d, [20-25]b=%d, w/b=%.2f%n",
        label,
        m.totalUnits,
        m.avgWords,
        m.avgBlocks,
        m.p50Blocks,
        m.p90Blocks,
        m.p95Blocks,
        m.maxBlocks,
        m.chunksUnder50Words,
        m.chunksOver25Blocks,
        m.chunks20to25Blocks,
        m.avgWordsPerBlock);
  }

  record Metrics(
      int totalUnits,
      double avgWords,
      double avgBlocks,
      double p50Blocks,
      double p90Blocks,
      double p95Blocks,
      int maxBlocks,
      int chunksUnder50Words,
      int chunksOver25Blocks,
      int chunks20to25Blocks,
      double avgWordsPerBlock) {}

  interface ChunkerAdapter {
    List<DocumentChunk> chunk(List<String> source, String language);
  }

  // Exact Legacy V4 Chunker implementation used strictly for benchmark comparison
  static class LegacyV4Chunker implements ChunkerAdapter {
    private static final int TARGET_WORDS = 160;
    private static final int MINIMUM_WORDS = 100;
    private static final int MAXIMUM_WORDS = 220;
    private static final int HARD_WORDS = 260;
    private static final int HARD_BYTES = 80 * 1024;
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{M}]+(?:['’][\\p{L}\\p{M}]+)*");
    private static final Pattern CONTEXT_BLOCK =
        Pattern.compile(
            "(?iu)^(?:act|book|chapter|part|scene|section)\\b.*|^\\[.*]|^[\\p{Lu}\\p{M} .'-]+$");

    @Override
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
        if (words(current) >= TARGET_WORDS && !isContext(block)) {
          add(chunks, current);
          current = new ArrayList<>();
        }
      }
      if (!current.isEmpty()) {
        if (!chunks.isEmpty() && words(current) < MINIMUM_WORDS) {
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

    private int wordCount(String value) {
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
      return words(blocks) > MAXIMUM_WORDS || bytes(blocks) > HARD_BYTES;
    }

    private boolean exceedsHard(List<String> blocks) {
      return words(blocks) > HARD_WORDS || bytes(blocks) > HARD_BYTES;
    }

    private boolean exceedsHard(CharSequence value) {
      return wordCount(value.toString()) > HARD_WORDS
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
}
