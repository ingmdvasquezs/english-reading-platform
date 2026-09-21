package com.soap.soap.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DocumentSectionTitleSanitizerTest {

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t", "\n", "   "})
  void rejectsNullAndBlankTitles(String title) {
    assertThat(DocumentSectionTitleSanitizer.sanitize(title)).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "id-idp140489363296560",
        "id-12345",
        "id_header",
        "idp140489363296560",
        "IDP9999",
        "x_id123",
        "calibre_id_42",
        "pgepubid0001",
        "_id000"
      })
  void rejectsTechnicalPrefixes(String technicalTitle) {
    assertThat(DocumentSectionTitleSanitizer.sanitize(technicalTitle)).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"#section1", "#c", "anchor-1", "fragment-42", "a-01"})
  void rejectsAnchorsAndFragments(String anchorTitle) {
    assertThat(DocumentSectionTitleSanitizer.sanitize(anchorTitle)).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "OEBPS/ch01s02.xhtml",
        "OPS/chapter1.html",
        "content/nav.xhtml",
        "folder\\subfolder\\file.xml",
        "/absolute/path.xhtml"
      })
  void rejectsSourceLocatorsAndPaths(String pathTitle) {
    assertThat(DocumentSectionTitleSanitizer.sanitize(pathTitle)).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "chapter1.xhtml",
        "ch01s02.xhtml",
        "index.html",
        "toc.ncx",
        "package.opf",
        "cover.jpg",
        "image.png"
      })
  void rejectsXhtmlAndOtherFilenames(String filename) {
    assertThat(DocumentSectionTitleSanitizer.sanitize(filename)).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "htmltoc",
        "HTMLTOC",
        "toc",
        "nav",
        "cover",
        "titlepage",
        "title_page",
        "page:12",
        "page_4",
        "page-7",
        "bk01-toc",
        "ch01s02",
        "c01",
        "f0dac4e1-a868-4eb1-92bc-d2107335c8ed",
        "urn:uuid:f0dac4e1-a868-4eb1-92bc-d2107335c8ed"
      })
  void rejectsTechnicalGeneratorAndInternalIds(String generatorId) {
    assertThat(DocumentSectionTitleSanitizer.sanitize(generatorId)).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Scene II",
        "Chapter III",
        "Act I",
        "The Gathering",
        "Chapter 3",
        "Part 1",
        "Prologue",
        "Epilogue",
        "The Forest of Arden",
        "1. The Beginning"
      })
  void preservesHumanEditorialHeadings(String humanTitle) {
    assertThat(DocumentSectionTitleSanitizer.sanitize(humanTitle)).isEqualTo(humanTitle);
  }

  @Test
  void stripsWhitespaceFromValidHumanTitle() {
    assertThat(DocumentSectionTitleSanitizer.sanitize("  Scene II  \n")).isEqualTo("Scene II");
  }
}
