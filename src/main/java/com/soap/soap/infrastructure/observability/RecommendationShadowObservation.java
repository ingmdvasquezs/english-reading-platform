package com.soap.soap.infrastructure.observability;

import com.soap.soap.application.model.RecommendationScoreV2;
import com.soap.soap.application.model.RecommendationShadowCandidate;
import com.soap.soap.application.port.out.RecommendationShadowPort;
import com.soap.soap.application.service.RecommendationScorerV2;
import com.soap.soap.domain.model.ReadingProgressStatus;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RecommendationShadowObservation implements RecommendationShadowPort {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(RecommendationShadowObservation.class);
  private static final int TOP_SIZE = 12;
  private static final Comparator<ShadowScore> V2_RANKING =
      Comparator.comparingInt(
              (ShadowScore score) -> progressPriority(score.candidate().reading().progressStatus()))
          .thenComparing(score -> score.v2Score().finalScore(), Comparator.reverseOrder())
          .thenComparing(
              score -> score.v2Score().classificationConfidence(), Comparator.reverseOrder())
          .thenComparing(score -> score.v2Score().lexicalChallenge())
          .thenComparingInt(score -> score.candidate().reading().editorialLevel().ordinal())
          .thenComparing(
              score -> score.candidate().reading().createdAt(),
              Comparator.nullsLast(Comparator.reverseOrder()))
          .thenComparing(score -> score.candidate().reading().title())
          .thenComparing(score -> score.candidate().reading().readingId());

  private final RecommendationScorerV2 scorer;
  private final MeterRegistry meters;
  private final boolean enabled;

  public RecommendationShadowObservation(
      RecommendationScorerV2 scorer,
      MeterRegistry meters,
      @Value("${recommendation.v2.shadow-enabled:true}") boolean enabled) {
    this.scorer = scorer;
    this.meters = meters;
    this.enabled = enabled;
  }

  @Override
  public void observe(Supplier<List<RecommendationShadowCandidate>> candidateSupplier) {
    if (!enabled) {
      meters.counter("recommendation.shadow.requests", "outcome", "disabled").increment();
      return;
    }
    var sample = Timer.start(meters);
    try {
      compare(candidateSupplier.get());
      meters.counter("recommendation.shadow.requests", "outcome", "success").increment();
    } catch (RuntimeException exception) {
      meters.counter("recommendation.shadow.requests", "outcome", "failure").increment();
      LOGGER.warn(
          "recommendation.shadow.failed errorType={}", exception.getClass().getSimpleName());
    } finally {
      sample.stop(Timer.builder("recommendation.shadow.duration").register(meters));
    }
  }

  private void compare(List<RecommendationShadowCandidate> candidates) {
    var v1 = candidates;
    var v2Unranked =
        candidates.stream()
            .map(
                candidate ->
                    new ShadowScore(
                        candidate,
                        scorer.score(candidate.v2Evidence(), candidate.reading().editorialLevel())))
            .toList();
    var v2 = v2Unranked.stream().sorted(V2_RANKING).toList();
    var v2ById =
        v2Unranked.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    item -> item.candidate().reading().readingId(), item -> item));
    var topSize = Math.min(TOP_SIZE, candidates.size());
    var topV1 = v1.subList(0, topSize);
    var topV2 = v2.subList(0, topSize);
    var topV1Scores = topV1.stream().map(item -> v2ById.get(item.reading().readingId())).toList();
    var rankV1 = ranksV1(v1);
    var rankV2 = ranksV2(v2);
    var overlap =
        topV1.stream()
            .map(item -> item.reading().readingId())
            .filter(
                topV2.stream().map(item -> item.candidate().reading().readingId()).toList()
                    ::contains)
            .count();
    var movements =
        candidates.stream()
            .mapToInt(
                item ->
                    Math.abs(
                        rankV1.get(item.reading().readingId())
                            - rankV2.get(item.reading().readingId())))
            .toArray();
    var meanMovement =
        movements.length == 0 ? 0.0 : java.util.Arrays.stream(movements).average().orElse(0.0);
    var maxMovement = java.util.Arrays.stream(movements).max().orElse(0);
    var spearman = spearman(rankV1, rankV2);

    summary("recommendation.shadow.candidates").record(candidates.size());
    summary("recommendation.shadow.top12.overlap").record(overlap);
    summary("recommendation.shadow.rank.mean_absolute_movement").record(meanMovement);
    summary("recommendation.shadow.rank.max_movement").record(maxMovement);
    summary("recommendation.shadow.rank.spearman").record(spearman);
    recordTopAverages("v1", topV1Scores);
    recordTopAverages("v2", topV2);
    recordBuckets(v1, v2, rankV1, rankV2);

    LOGGER.info(
        "recommendation.shadow.completed timestamp={} candidates={} top12V1={} top12V2={} top12OverlapCount={} meanAbsoluteRankMovement={} maxRankMovement={} spearman={} learningMeanV1={} learningMeanV2={} tokenKnownCoverageMeanV1={} tokenKnownCoverageMeanV2={} lexicalChallengeMeanV1={} lexicalChallengeMeanV2={}",
        java.time.Instant.now(),
        candidates.size(),
        topV1.stream().map(item -> item.reading().readingId()).toList(),
        topV2.stream().map(item -> item.candidate().reading().readingId()).toList(),
        overlap,
        meanMovement,
        maxMovement,
        spearman,
        meanLearning(topV1Scores),
        meanLearning(topV2),
        meanTokenKnown(topV1Scores),
        meanTokenKnown(topV2),
        meanChallenge(topV1Scores),
        meanChallenge(topV2));
    logTopCandidates(topV1, v2, rankV1, rankV2);
  }

  private void recordTopAverages(String scorerName, List<ShadowScore> top) {
    summary("recommendation.shadow.top12.learning.mean", "scorer", scorerName)
        .record(meanLearning(top));
    summary("recommendation.shadow.top12.token_known_coverage.mean", "scorer", scorerName)
        .record(meanTokenKnown(top));
    summary("recommendation.shadow.top12.lexical_challenge.mean", "scorer", scorerName)
        .record(meanChallenge(top));
  }

  private void recordBuckets(
      List<RecommendationShadowCandidate> v1,
      List<ShadowScore> v2,
      Map<UUID, Integer> rankV1,
      Map<UUID, Integer> rankV2) {
    var v2ById =
        v2.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    item -> item.candidate().reading().readingId(), item -> item));
    for (var candidate : v1) {
      var id = candidate.reading().readingId();
      var shadow = v2ById.get(id);
      var progress = progressBucket(candidate.reading().progressStatus());
      var confidence = confidenceBucket(shadow.v2Score().classificationConfidence().doubleValue());
      summary(
              "recommendation.shadow.score",
              "scorer",
              "v1",
              "progress",
              progress,
              "confidence",
              confidence)
          .record(candidate.v1Score().score().doubleValue());
      summary(
              "recommendation.shadow.score",
              "scorer",
              "v2",
              "progress",
              progress,
              "confidence",
              confidence)
          .record(shadow.v2Score().finalScore().doubleValue());
      summary("recommendation.shadow.rank.movement", "progress", progress, "confidence", confidence)
          .record(Math.abs(rankV1.get(id) - rankV2.get(id)));
    }
  }

  private void logTopCandidates(
      List<RecommendationShadowCandidate> v1,
      List<ShadowScore> v2,
      Map<UUID, Integer> rankV1,
      Map<UUID, Integer> rankV2) {
    var topIds = new java.util.LinkedHashSet<UUID>();
    v1.stream().limit(TOP_SIZE).map(item -> item.reading().readingId()).forEach(topIds::add);
    v2.stream()
        .limit(TOP_SIZE)
        .map(item -> item.candidate().reading().readingId())
        .forEach(topIds::add);
    var byId =
        v2.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    item -> item.candidate().reading().readingId(), item -> item));
    topIds.forEach(
        id -> {
          var item = byId.get(id);
          var candidate = item.candidate();
          var reading = candidate.reading();
          var evidence = candidate.v2Evidence();
          var score = item.v2Score();
          LOGGER.debug(
              "recommendation.shadow.candidate readingId={} editorialLevel={} progressStatus={} rankV1={} rankV2={} v1Score={} v2Score={} classificationConfidence={} knownUnique={} learningUnique={} explicitNewUnique={} unclassifiedUnique={} tokenKnownCoverage={} learningReinforcement={} lexicalChallenge={}",
              id,
              reading.editorialLevel(),
              progressBucket(reading.progressStatus()),
              rankV1.get(id),
              rankV2.get(id),
              candidate.v1Score().score(),
              score.finalScore(),
              score.classificationConfidence(),
              evidence.knownUniqueWords(),
              evidence.learningUniqueWords(),
              evidence.explicitNewUniqueWords(),
              evidence.unclassifiedUniqueWords(),
              score.tokenKnownCoverage(),
              score.learningReinforcement(),
              score.lexicalChallenge());
        });
  }

  private double meanLearning(List<ShadowScore> values) {
    return values.stream()
        .mapToInt(item -> item.candidate().v2Evidence().learningUniqueWords())
        .average()
        .orElse(0.0);
  }

  private double meanTokenKnown(List<ShadowScore> values) {
    return values.stream()
        .mapToDouble(value -> value.v2Score().tokenKnownCoverage().doubleValue())
        .average()
        .orElse(0.0);
  }

  private double meanChallenge(List<ShadowScore> values) {
    return values.stream()
        .mapToDouble(value -> value.v2Score().lexicalChallenge().doubleValue())
        .average()
        .orElse(0.0);
  }

  private Map<UUID, Integer> ranksV1(List<RecommendationShadowCandidate> ranked) {
    var result = new HashMap<UUID, Integer>();
    for (var index = 0; index < ranked.size(); index++) {
      result.put(ranked.get(index).reading().readingId(), index + 1);
    }
    return result;
  }

  private Map<UUID, Integer> ranksV2(List<ShadowScore> ranked) {
    var result = new HashMap<UUID, Integer>();
    for (var index = 0; index < ranked.size(); index++) {
      result.put(ranked.get(index).candidate().reading().readingId(), index + 1);
    }
    return result;
  }

  private double spearman(Map<UUID, Integer> first, Map<UUID, Integer> second) {
    var size = first.size();
    if (size < 2) {
      return 1.0;
    }
    var squaredDifferences =
        first.entrySet().stream()
            .mapToDouble(
                entry -> {
                  var difference = entry.getValue() - second.get(entry.getKey());
                  return (double) difference * difference;
                })
            .sum();
    return 1.0 - (6.0 * squaredDifferences) / (size * ((double) size * size - 1.0));
  }

  private DistributionSummary summary(String name, String... tags) {
    return DistributionSummary.builder(name).tags(tags).register(meters);
  }

  private static int progressPriority(ReadingProgressStatus status) {
    return status == null ? 0 : status == ReadingProgressStatus.IN_PROGRESS ? 1 : 2;
  }

  private String progressBucket(ReadingProgressStatus status) {
    return status == null ? "NOT_STARTED" : status.name();
  }

  private String confidenceBucket(double confidence) {
    if (confidence < 5.0) return "0-5";
    if (confidence < 10.0) return "5-10";
    if (confidence < 25.0) return "10-25";
    if (confidence < 50.0) return "25-50";
    return "50+";
  }

  private record ShadowScore(
      RecommendationShadowCandidate candidate, RecommendationScoreV2 v2Score) {}
}
