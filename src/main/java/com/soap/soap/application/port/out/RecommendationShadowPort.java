package com.soap.soap.application.port.out;

import com.soap.soap.application.model.RecommendationShadowCandidate;
import java.util.List;
import java.util.function.Supplier;

@FunctionalInterface
public interface RecommendationShadowPort {
  void observe(Supplier<List<RecommendationShadowCandidate>> candidates);
}
