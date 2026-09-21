package com.soap.soap.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.soap.soap.domain.service.DocumentSectionTitleSanitizer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AsYouLikeItV5ReimportValidationTest {

  private final DocumentChunker chunker = new DocumentChunker();

  @Test
  void reimportV5TestCopyAndVerifyInDatabase() throws Exception {
    try (var conn =
        DriverManager.getConnection(
            "jdbc:postgresql://localhost:5432/english_reading",
            "english_user",
            "english_password")) {

      conn.setAutoCommit(false);

      var originalDocId = UUID.fromString("a459fdf7-6c76-40d2-821b-0ad159bc420a");

      // 1. Verify original document remains intact
      UUID userId;
      String author;
      String language;
      String format;
      try (var ps =
          conn.prepareStatement(
              "SELECT user_id, author, language, format, chunking_version FROM imported_documents WHERE id = ?")) {
        ps.setObject(1, originalDocId);
        try (var rs = ps.executeQuery()) {
          assertThat(rs.next()).isTrue();
          userId = (UUID) rs.getObject("user_id");
          author = rs.getString("author");
          language = rs.getString("language");
          format = rs.getString("format");
          int origChunkingVersion = rs.getInt("chunking_version");
          assertThat(origChunkingVersion).isEqualTo(3);
        }
      }

      int origUnits;
      try (var ps =
          conn.prepareStatement("SELECT count(*) FROM document_units WHERE document_id = ?")) {
        ps.setObject(1, originalDocId);
        try (var rs = ps.executeQuery()) {
          assertThat(rs.next()).isTrue();
          origUnits = rs.getInt(1);
          assertThat(origUnits).isEqualTo(61);
        }
      }

      // Check if a previous V5 test copy already exists, delete if re-running test
      try (var ps =
          conn.prepareStatement(
              "DELETE FROM imported_documents WHERE user_id = ? AND title = 'As You Like It (V5 Validation Copy)'")) {
        ps.setObject(1, userId);
        ps.executeUpdate();
      }

      // 2. Create new test copy of the document with chunking_version = 5
      var newDocId = UUID.randomUUID();
      var now = Timestamp.from(Instant.now());
      var dummySha =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8)));

      try (var ps =
          conn.prepareStatement(
              "INSERT INTO imported_documents (id, user_id, title, author, language, format, import_status, "
                  + "source_sha256, deduplication_sha256, chunking_version, created_at, updated_at) "
                  + "VALUES (?, ?, 'As You Like It (V5 Validation Copy)', ?, ?, ?, 'READY', ?, ?, 5, ?, ?)")) {
        ps.setObject(1, newDocId);
        ps.setObject(2, userId);
        ps.setString(3, author);
        ps.setString(4, language);
        ps.setString(5, format);
        ps.setString(6, dummySha);
        ps.setString(7, dummySha);
        ps.setTimestamp(8, now);
        ps.setTimestamp(9, now);
        ps.executeUpdate();
      }

      // 3. Load sections and their blocks from original document
      record SectionData(UUID oldId, int ordinal, String rawTitle, String sourceLocator) {}
      var sections = new ArrayList<SectionData>();
      try (var ps =
          conn.prepareStatement(
              "SELECT id, ordinal, title, source_locator FROM document_sections WHERE document_id = ? ORDER BY ordinal")) {
        ps.setObject(1, originalDocId);
        try (var rs = ps.executeQuery()) {
          while (rs.next()) {
            sections.add(
                new SectionData(
                    (UUID) rs.getObject("id"),
                    rs.getInt("ordinal"),
                    rs.getString("title"),
                    rs.getString("source_locator")));
          }
        }
      }

      var globalOrdinal = 1;
      var md = MessageDigest.getInstance("SHA-256");

      for (var sec : sections) {
        var blocks = new ArrayList<String>();
        try (var ps =
            conn.prepareStatement(
                "SELECT content FROM document_units WHERE section_id = ? ORDER BY section_ordinal")) {
          ps.setObject(1, sec.oldId());
          try (var rs = ps.executeQuery()) {
            while (rs.next()) {
              for (var p : rs.getString("content").split("\n\n")) {
                var stripped = p.strip();
                if (!stripped.isEmpty()) blocks.add(stripped);
              }
            }
          }
        }

        var newSecId = UUID.randomUUID();
        var sanitizedTitle = DocumentSectionTitleSanitizer.sanitize(sec.rawTitle());

        try (var ps =
            conn.prepareStatement(
                "INSERT INTO document_sections (id, document_id, ordinal, title, source_locator) VALUES (?, ?, ?, ?, ?)")) {
          ps.setObject(1, newSecId);
          ps.setObject(2, newDocId);
          ps.setInt(3, sec.ordinal());
          ps.setString(4, sanitizedTitle);
          ps.setString(5, sec.sourceLocator());
          ps.executeUpdate();
        }

        var chunks = chunker.chunk(blocks, "en");
        for (int i = 0; i < chunks.size(); i++) {
          var chunk = chunks.get(i);
          var unitId = UUID.randomUUID();
          var hash =
              HexFormat.of().formatHex(md.digest(chunk.content().getBytes(StandardCharsets.UTF_8)));

          try (var ps =
              conn.prepareStatement(
                  "INSERT INTO document_units (id, document_id, section_id, global_ordinal, section_ordinal, "
                      + "unit_kind, content, word_count, source_locator, content_hash) "
                      + "VALUES (?, ?, ?, ?, ?, 'LOGICAL_CHUNK', ?, ?, ?, ?)")) {
            ps.setObject(1, unitId);
            ps.setObject(2, newDocId);
            ps.setObject(3, newSecId);
            ps.setInt(4, globalOrdinal++);
            ps.setInt(5, i + 1);
            ps.setString(6, chunk.content());
            ps.setInt(7, chunk.wordCount());
            ps.setString(8, sec.sourceLocator());
            ps.setString(9, hash);
            ps.executeUpdate();
          }
        }
      }

      conn.commit();

      // 4. Verify in database
      System.out.println("=== V5 REIMPORT VALIDATION COPY ===");
      System.out.println("V5 Document ID: " + newDocId);

      // Verify chunking_version = 5
      try (var ps =
          conn.prepareStatement("SELECT chunking_version FROM imported_documents WHERE id = ?")) {
        ps.setObject(1, newDocId);
        try (var rs = ps.executeQuery()) {
          assertThat(rs.next()).isTrue();
          assertThat(rs.getInt(1)).isEqualTo(5);
        }
      }

      // Verify unit count = 141
      int totalUnits;
      try (var ps =
          conn.prepareStatement("SELECT count(*) FROM document_units WHERE document_id = ?")) {
        ps.setObject(1, newDocId);
        try (var rs = ps.executeQuery()) {
          assertThat(rs.next()).isTrue();
          totalUnits = rs.getInt(1);
          assertThat(totalUnits).isEqualTo(141);
        }
      }

      // Verify block metrics
      var blockCounts = new ArrayList<Integer>();
      try (var ps =
          conn.prepareStatement(
              "SELECT array_length(string_to_array(content, E'\\n\\n'), 1) as b FROM document_units WHERE document_id = ?")) {
        ps.setObject(1, newDocId);
        try (var rs = ps.executeQuery()) {
          while (rs.next()) {
            blockCounts.add(rs.getInt("b"));
          }
        }
      }

      Collections.sort(blockCounts);
      int maxBlocks = blockCounts.getLast();
      assertThat(maxBlocks).isLessThanOrEqualTo(25);
      assertThat(maxBlocks).isEqualTo(23);

      long unitsOver25Blocks = blockCounts.stream().filter(b -> b > 25).count();
      assertThat(unitsOver25Blocks).isEqualTo(0);

      int p95Index = (int) Math.ceil(0.95 * blockCounts.size()) - 1;
      int p95Blocks = blockCounts.get(p95Index);
      assertThat(p95Blocks).isEqualTo(20);

      // Verify section titles: no technical IDs
      try (var ps =
          conn.prepareStatement(
              "SELECT ordinal, title, source_locator FROM document_sections WHERE document_id = ? ORDER BY ordinal")) {
        ps.setObject(1, newDocId);
        try (var rs = ps.executeQuery()) {
          while (rs.next()) {
            var title = rs.getString("title");
            if (title != null) {
              assertThat(title).doesNotContain("id-");
              assertThat(title).doesNotContain(".xhtml");
              assertThat(title).doesNotContain("htmltoc");
            }
          }
        }
      }

      // Verify human titles like Scene II, Act I preserved
      try (var ps =
          conn.prepareStatement(
              "SELECT title FROM document_sections WHERE document_id = ? AND ordinal = 5")) {
        ps.setObject(1, newDocId);
        try (var rs = ps.executeQuery()) {
          assertThat(rs.next()).isTrue();
          assertThat(rs.getString("title")).isEqualTo("Scene II");
        }
      }

      // Verify original document is completely intact
      try (var ps =
          conn.prepareStatement("SELECT chunking_version FROM imported_documents WHERE id = ?")) {
        ps.setObject(1, originalDocId);
        try (var rs = ps.executeQuery()) {
          assertThat(rs.next()).isTrue();
          assertThat(rs.getInt(1)).isEqualTo(3);
        }
      }
      try (var ps =
          conn.prepareStatement("SELECT count(*) FROM document_units WHERE document_id = ?")) {
        ps.setObject(1, originalDocId);
        try (var rs = ps.executeQuery()) {
          assertThat(rs.next()).isTrue();
          assertThat(rs.getInt(1)).isEqualTo(origUnits);
        }
      }

      System.out.printf(
          "SUCCESS: V5 Copy %s created with 141 units, maxBlocks=%d, p95=%d, >25b=%d, original doc %s intact.%n",
          newDocId, maxBlocks, p95Blocks, unitsOver25Blocks, originalDocId);
    }
  }
}
