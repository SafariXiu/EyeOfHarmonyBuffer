package com.EyeOfHarmonyBuffer.space.talos.chunk.field.diagnostics;

import java.time.Duration;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Objects;
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

    public FieldDiagnostics() {
        for (FieldDomain domain : FieldDomain.values()) {
            requestCounts.put(domain, new LongAdder());
            providerCalls.put(domain, new LongAdder());
            providerErrors.put(domain, new LongAdder());
            providerNanos.put(domain, new LongAdder());
        }
    }

    public void recordSample(EnumSet<FieldDomain> domains) {
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
        if (token == null) {
            return;
        }
        token.close();
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
            .totalRequests(aggregateRequests.sum());

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
            if (closed) {
                return;
            }
            closed = true;
            diagnostics.finish(domain, System.nanoTime() - startNanos);
        }

        public FieldDomain domain() {
            return domain;
        }
    }

    public static final class DiagnosticsReport {

        private final long totalRequests;
        private final EnumMap<FieldDomain, DomainMetrics> metrics;

        private DiagnosticsReport(long totalRequests, EnumMap<FieldDomain, DomainMetrics> metrics) {
            this.totalRequests = totalRequests;
            this.metrics = metrics;
        }

        public long getTotalRequests() {
            return totalRequests;
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

        public EnumMap<FieldDomain, DomainMetrics> allMetrics() {
            return new EnumMap<>(metrics);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {

            private long totalRequests;
            private final EnumMap<FieldDomain, DomainMetrics> metrics = new EnumMap<>(FieldDomain.class);

            private Builder() {
                for (FieldDomain domain : FieldDomain.values()) {
                    metrics.put(domain, new DomainMetrics(0L, 0L, 0L, 0L));
                }
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

            public DiagnosticsReport build() {
                return new DiagnosticsReport(totalRequests, metrics);
            }
        }
    }
}
