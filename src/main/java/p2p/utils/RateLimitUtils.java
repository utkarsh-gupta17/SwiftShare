package p2p.utils;

import java.io.*;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimitUtils {
    public static class RateLimitEntry {
        public long windowStart;
        public int count;

        public RateLimitEntry(long windowStart) {
            this.windowStart = windowStart;
            this.count = 1;
        }
    }

    public static boolean checkRateLimit(ConcurrentHashMap<String, RateLimitEntry> map, String ip, int maxRequests, long windowMs) {
        long now = System.currentTimeMillis();
        RateLimitEntry entry = map.compute(ip, (key, existing) -> {
            if (existing == null || (now - existing.windowStart) > windowMs) {
                return new RateLimitEntry(now);
            } else {
                existing.count++;
                return existing;
            }
        });

        return entry.count > maxRequests;
    }
}
