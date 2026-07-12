package br.com.schf.security.ratelimit;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {

    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimitService(Clock clock) {
        this.clock = clock;
    }

    public boolean allow(String key, int limit, long windowSeconds) {
        var window = Math.floorDiv(clock.instant().getEpochSecond(), windowSeconds);
        var counter = counters.compute(key, (ignored, current) -> {
            if (current == null || current.window() != window) {
                return new WindowCounter(window, 1);
            }
            return new WindowCounter(window, current.count() + 1);
        });
        return counter.count() <= limit;
    }

    public void reset() {
        counters.clear();
    }

    private record WindowCounter(long window, int count) {
    }
}
