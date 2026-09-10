package com.soap.soap.spike.epub;

import java.util.List;

final class EpubSpikeModel {

  private EpubSpikeModel() {}

  enum Status {
    SUPPORTED,
    UNSUPPORTED_DRM,
    INVALID_EPUB
  }

  record Metadata(String title, String author, String language, String packagePath) {}

  record Cover(String mediaType, byte[] bytes) {
    Cover {
      bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
      return bytes.clone();
    }
  }

  record Block(Type type, String text) {
    enum Type {
      HEADING,
      PARAGRAPH,
      BLOCKQUOTE,
      LIST_ITEM
    }
  }

  record Section(String id, String href, List<Block> blocks) {
    Section {
      blocks = List.copyOf(blocks);
    }
  }

  record Chunk(int ordinal, List<Block> blocks, int wordCount, int utf8Bytes) {
    Chunk {
      blocks = List.copyOf(blocks);
    }

    String text() {
      return blocks.stream()
          .map(Block::text)
          .reduce((left, right) -> left + "\n\n" + right)
          .orElse("");
    }
  }

  record Parsed(
      Status status,
      Metadata metadata,
      Cover cover,
      List<Section> sections,
      int manifestItems,
      long elapsedMillis) {
    Parsed {
      sections = List.copyOf(sections);
    }
  }
}
