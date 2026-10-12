package com.splitwise.split;

import com.splitwise.domain.SplitType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Looks up the {@link SplitStrategy} registered for a {@link SplitType}. */
@Component
public class SplitStrategyResolver {

    private final Map<SplitType, SplitStrategy> strategies = new EnumMap<>(SplitType.class);

    public SplitStrategyResolver(List<SplitStrategy> strategies) {
        for (SplitStrategy s : strategies) {
            if (this.strategies.put(s.type(), s) != null) {
                throw new IllegalStateException("Duplicate split strategy for " + s.type());
            }
        }
        for (SplitType type : SplitType.values()) {
            if (!this.strategies.containsKey(type)) {
                throw new IllegalStateException("No split strategy registered for " + type);
            }
        }
    }

    public SplitStrategy forType(SplitType type) {
        return strategies.get(type);
    }
}
