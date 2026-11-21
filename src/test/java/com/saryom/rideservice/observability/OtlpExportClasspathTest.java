package com.saryom.rideservice.observability;

import io.micrometer.core.instrument.Clock;
import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Builds each OTLP exporter the way the application does when OTLP_ENABLED=true.
 *
 * <p>Exporting is off in every other test, so without this a dependency clash
 * in the exporters (protobuf 3 from firebase-admin, a missing HTTP sender)
 * would pass the build and only show up as a service that fails to start once
 * observability is switched on in production. Nothing here touches the network.
 */
class OtlpExportClasspathTest {

    @Test
    void metricsExporterStarts() {
        Map<String, String> settings = Map.of("otlp.url", "http://127.0.0.1:1/v1/metrics");
        assertThatCode(() -> {
            OtlpMeterRegistry registry = new OtlpMeterRegistry((OtlpConfig) settings::get, Clock.SYSTEM);
            registry.counter("classpath.check").increment();
            registry.timer("classpath.timer").record(java.time.Duration.ofMillis(5));
            registry.getMeters().forEach(meter -> meter.measure());
        }).doesNotThrowAnyException();
    }

    @Test
    void traceExporterStarts() {
        assertThatCode(() -> OtlpHttpSpanExporter.builder()
                .setEndpoint("http://127.0.0.1:1/v1/traces").build().close())
                .doesNotThrowAnyException();
    }

    @Test
    void logExporterStarts() {
        assertThatCode(() -> OtlpHttpLogRecordExporter.builder()
                .setEndpoint("http://127.0.0.1:1/v1/logs").build().close())
                .doesNotThrowAnyException();
    }
}
