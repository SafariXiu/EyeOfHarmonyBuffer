package com.EyeOfHarmonyBuffer.space.talos.chunk.field.diagnostics;

import java.time.Duration;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import com.EyeOfHarmonyBuffer.space.talos.chunk.field.context.FieldDomain;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class FieldDiagnostics {

    private static final Logger LOGGER = LogManager.getLogger(FieldDiagnostics.class);

    private final EnumMap<FieldDomain, LongAdder> requestCounts = new EnumMap<>(FieldDomain.class);
    private final EnumMap<FieldDomain, LongAdder> providerCalls = new EnumMap<>(FieldDomain.class);
    private final EnumMap<FieldDomain, LongAdder> providerErrors = new EnumMap<>(FieldDomain.class);
    private final EnumMap<FieldDomain, LongAdder> providerNanos = new EnumMap<>(FieldDomain.class);

    private final LongAdder aggregateRequests = new LongAdder();
    private final MacroCacheMetrics macroCacheMetrics;
    private final MacroCacheProbe macroCacheProbe;

    public FieldDiagnostics() {
        this(true);
    }

    public void recordSample(java.util.EnumSet<FieldDomain> domains) {
        aggregateRequests.increment();
        for (FieldDomain domain : domains) {
            requestCounts.get(domain).increment();
        }
    }

    public SampleToken begin(FieldDomain domain) {
        Objects.requireNonNull(domain, "domain");
        providerCalls.get(domain).increment();
        return new SampleToken(domain, System.nanoTime(), this);
    }

    public void end(SampleToken token) {
        if (token != null) {
            token.close();
        }
    }

    void finish(FieldDomain domain, long elapsedNanos) {
        providerNanos.get(domain).add(Math.max(0L, elapsedNanos));
    }

    public void recordError(FieldDomain domain, Throwable error) {
        providerErrors.get(domain).increment();
        if (error != null) {
            LOGGER.log(Level.DEBUG,
                "[FieldDiagnostics] {} provider error: {}",
                domain, error.getMessage(), error);
        }
    }

    public DiagnosticsReport snapshot() {
        DiagnosticsReport.Builder builder = DiagnosticsReport.builder()
            .totalRequests(aggregateRequests.sum())
            .macroCache(macroCacheMetrics.snapshot());

        for (FieldDomain domain : FieldDomain.values()) {
            builder.withDomainMetrics(
                domain,
                requestCounts.get(domain).sum(),
                providerCalls.get(domain).sum(),
                providerErrors.get(domain).sum(),
                providerNanos.get(domain).sum()
            );
        }
        return builder.build();
    }

    public FieldDiagnostics(boolean diagnosticsEnabled) {
        this.macroCacheMetrics = diagnosticsEnabled
            ? MacroCacheMetrics.create()
            : MacroCacheMetrics.noop();

        this.macroCacheProbe = diagnosticsEnabled
            ? new SlidingWindowMacroCacheProbe(macroCacheMetrics)
            : MacroCacheProbe.NOOP;

        initializeDomainCounters();
    }

    public MacroCacheProbe macroCache() {
        return macroCacheProbe;
    }

    public static final class SampleToken implements AutoCloseable {
        private final FieldDomain domain;
        private final long startNanos;
        private final FieldDiagnostics diagnostics;
        private boolean closed;

        private SampleToken(FieldDomain domain, long startNanos, FieldDiagnostics diagnostics) {
            this.domain = domain;
            this.startNanos = startNanos;
            this.diagnostics = diagnostics;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                diagnostics.finish(domain, System.nanoTime() - startNanos);
            }
        }

        public FieldDomain domain() {
            return domain;
        }
    }

    public static final class DiagnosticsReport {

        private final long totalRequests;
        private final EnumMap<FieldDomain, DomainMetrics> metrics;
        private final MacroCacheSnapshot macroCacheSnapshot;

        private DiagnosticsReport(long totalRequests,
                                  EnumMap<FieldDomain, DomainMetrics> metrics,
                                  MacroCacheSnapshot macroCacheSnapshot) {
            this.totalRequests = totalRequests;
            this.metrics = metrics;
            this.macroCacheSnapshot = macroCacheSnapshot;
        }

        public long getTotalRequests() {
            return totalRequests;
        }

        public EnumMap<FieldDomain, DomainMetrics> allMetrics() {
            return new EnumMap<>(metrics);
        }

        public MacroCacheSnapshot macroCache() {
            return macroCacheSnapshot;
        }

        public static final class DomainMetrics {
            private final long requested;
            private final long providerCalls;
            private final long providerErrors;
            private final long totalNanos;

            public DomainMetrics(long requested,
                                 long providerCalls,
                                 long providerErrors,
                                 long totalNanos) {
                this.requested = requested;
                this.providerCalls = providerCalls;
                this.providerErrors = providerErrors;
                this.totalNanos = totalNanos;
            }

            public long getRequested()      { return requested; }
            public long getProviderCalls()  { return providerCalls; }
            public long getProviderErrors() { return providerErrors; }
            public long getTotalNanos()     { return totalNanos; }

            public double averageMillis() {
                if (providerCalls == 0 || totalNanos == 0) {
                    return 0.0;
                }
                return (totalNanos / 1_000_000.0) / providerCalls;
            }

            public Duration averageDuration() {
                return Duration.ofNanos(providerCalls == 0 ? 0 : totalNanos / providerCalls);
            }
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {

            private long totalRequests;
            private final EnumMap<FieldDomain, DomainMetrics> metrics = new EnumMap<>(FieldDomain.class);
            private MacroCacheSnapshot macroCacheSnapshot = MacroCacheSnapshot.empty();

            private Builder() {
                Arrays.stream(FieldDomain.values())
                    .forEach(domain -> metrics.put(domain, new DomainMetrics(0L, 0L, 0L, 0L)));
            }

            public Builder totalRequests(long totalRequests) {
                this.totalRequests = totalRequests;
                return this;
            }

            public Builder withDomainMetrics(FieldDomain domain,
                                             long requested,
                                             long providerCalls,
                                             long providerErrors,
                                             long totalNanos) {
                metrics.put(domain, new DomainMetrics(requested, providerCalls, providerErrors, totalNanos));
                return this;
            }

            public Builder macroCache(MacroCacheSnapshot snapshot) {
                this.macroCacheSnapshot = snapshot;
                return this;
            }

            public DiagnosticsReport build() {
                return new DiagnosticsReport(totalRequests, metrics, macroCacheSnapshot);
            }
        }
    }

    public static final class MacroCacheMetrics {

        private static final MacroCacheMetrics NOOP = new MacroCacheMetrics(true);

        private final boolean noop;
        private final LongAdder hits = new LongAdder();
        private final LongAdder misses = new LongAdder();
        private final LongAdder peekHits = new LongAdder();
        private final LongAdder peekMisses = new LongAdder();
        private final LongAdder evictions = new LongAdder();
        private final LongAdder buildNanos = new LongAdder();

        private MacroCacheMetrics(boolean noop) {
            this.noop = noop;
        }

        public static MacroCacheMetrics create() {
            return new MacroCacheMetrics(false);
        }

        public static MacroCacheMetrics noop() {
            return NOOP;
        }

        public void recordHit()       { if (!noop) hits.increment(); }
        public void recordMiss()      { if (!noop) misses.increment(); }
        public void recordPeekHit()   { if (!noop) peekHits.increment(); }
        public void recordPeekMiss()  { if (!noop) peekMisses.increment(); }
        public void recordEviction()  { if (!noop) evictions.increment(); }
        public void recordBuildNanos(long nanos) {
            if (!noop && nanos > 0L) {
                buildNanos.add(nanos);
            }
        }

        public MacroCacheSnapshot snapshot() {
            if (noop) {
                return MacroCacheSnapshot.empty();
            }
            return new MacroCacheSnapshot(
                hits.sum(),
                misses.sum(),
                peekHits.sum(),
                peekMisses.sum(),
                evictions.sum(),
                buildNanos.sum()
            );
        }

        public void reset() {
            if (noop) {
                return;
            }
            hits.reset();
            misses.reset();
            peekHits.reset();
            peekMisses.reset();
            evictions.reset();
            buildNanos.reset();
        }
    }

    public static final class MacroCacheSnapshot {
        private static final MacroCacheSnapshot EMPTY = new MacroCacheSnapshot(0, 0, 0, 0, 0, 0);

        private final long hits;
        private final long misses;
        private final long peekHits;
        private final long peekMisses;
        private final long evictions;
        private final long buildNanos;

        private MacroCacheSnapshot(long hits,
                                   long misses,
                                   long peekHits,
                                   long peekMisses,
                                   long evictions,
                                   long buildNanos) {
            this.hits = hits;
            this.misses = misses;
            this.peekHits = peekHits;
            this.peekMisses = peekMisses;
            this.evictions = evictions;
            this.buildNanos = buildNanos;
        }

        public static MacroCacheSnapshot empty() {
            return EMPTY;
        }

        public long getHits()       { return hits; }
        public long getMisses()     { return misses; }
        public long getPeekHits()   { return peekHits; }
        public long getPeekMisses() { return peekMisses; }
        public long getEvictions()  { return evictions; }
        public long getBuildNanos() { return buildNanos; }

        public double hitRate() {
            long total = hits + misses;
            return total == 0 ? 0.0 : hits / (double) total;
        }

        public double averageBuildMillis() {
            long builds = Math.max(1L, hits + misses);
            return (buildNanos / 1_000_000.0) / builds;
        }
    }

    private static final class SlidingWindowMacroCacheProbe implements MacroCacheProbe {
        private final AtomicLong hits = new AtomicLong();
        private final AtomicLong misses = new AtomicLong();
        private final AtomicLong totalLoadNanos = new AtomicLong();
        private final AtomicLong evictions = new AtomicLong();
        private final MacroCacheMetrics metrics;

        private SlidingWindowMacroCacheProbe(MacroCacheMetrics metrics) {
            this.metrics = Objects.requireNonNull(metrics, "metrics");
        }

        @Override public void recordHit() {
            hits.incrementAndGet();
            metrics.recordHit();
        }

        @Override public void recordMiss() {
            misses.incrementAndGet();
            metrics.recordMiss();
        }

        @Override public void recordLoadNanos(long nanos) {
            long clamped = Math.max(0L, nanos);
            totalLoadNanos.addAndGet(clamped);
            metrics.recordBuildNanos(clamped);
        }

        @Override public void recordEviction() {
            evictions.incrementAndGet();
            metrics.recordEviction();
        }

        @Override
        public MacroCacheStats snapshot() {
            return new MacroCacheStats(
                hits.get(),
                misses.get(),
                totalLoadNanos.get(),
                evictions.get()
            );
        }

        @Override
        public void reset() {
            hits.set(0L);
            misses.set(0L);
            totalLoadNanos.set(0L);
            evictions.set(0L);
        }
    }

    private void initializeDomainCounters() {
        for (FieldDomain domain : FieldDomain.values()) {
            requestCounts.putIfAbsent(domain, new LongAdder());
            providerCalls.putIfAbsent(domain, new LongAdder());
            providerErrors.putIfAbsent(domain, new LongAdder());
            providerNanos.putIfAbsent(domain, new LongAdder());
        }
    }

    public void resetAll() {
        aggregateRequests.reset();
        requestCounts.values().forEach(LongAdder::reset);
        providerCalls.values().forEach(LongAdder::reset);
        providerErrors.values().forEach(LongAdder::reset);
        providerNanos.values().forEach(LongAdder::reset);
        resetMacroCacheStats();
    }

    public void resetMacroCacheStats() {
        macroCacheProbe.reset();
        macroCacheMetrics.reset();
    }
}
