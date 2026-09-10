package com.soap.soap.spike.epub;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EpubSpikePerformanceTest {

  @TempDir Path temporaryDirectory;

  @Test
  void recordsApproximateExtractionAndChunkingMetricsForAReasonablyLargeFixture() throws Exception {
    var paragraphs =
        IntStream.range(0, 400)
            .mapToObj(index -> "<p>" + words(120, "word" + index) + "</p>")
            .collect(Collectors.joining());
    var epub =
        EpubFixtureFactory.custom(
            temporaryDirectory.resolve("large.epub"),
            EpubFixtureFactory.simpleOpf("large.xhtml"),
            Map.of(
                "OPS/large.xhtml",
                EpubFixtureFactory.xhtml("Large", "<h1>Large</h1>" + paragraphs)
                    .getBytes(StandardCharsets.UTF_8)));

    var beforeMemory = usedMemory();
    var parsed = new EpubSpikeParser().parse(epub);
    var chunks =
        new PrototypeChunker().chunk(parsed.sections().getFirst().blocks(), Locale.ENGLISH);
    var memoryDelta = Math.max(0, usedMemory() - beforeMemory);

    assertThat(parsed.sections()).hasSize(1);
    assertThat(parsed.sections().getFirst().blocks()).hasSize(401);
    assertThat(chunks).hasSizeGreaterThan(40);
    assertThat(parsed.elapsedMillis()).isLessThan(10_000);
    System.out.printf(
        "EPUB_SPIKE_METRICS archiveBytes=%d extractionMs=%d memoryDeltaBytes=%d sections=%d paragraphs=%d words=%d chunks=%d%n",
        java.nio.file.Files.size(epub),
        parsed.elapsedMillis(),
        memoryDelta,
        parsed.sections().size(),
        parsed.sections().getFirst().blocks().size() - 1,
        chunks.stream().mapToInt(EpubSpikeModel.Chunk::wordCount).sum(),
        chunks.size());
  }

  private String words(int count, String prefix) {
    return IntStream.range(0, count)
        .mapToObj(index -> prefix + "_" + index)
        .collect(Collectors.joining(" "));
  }

  private long usedMemory() {
    var runtime = Runtime.getRuntime();
    return runtime.totalMemory() - runtime.freeMemory();
  }
}
