package com.saryom.rideservice.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.micrometer.observation.ObservationPredicate;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * Wiring that Spring Boot's OpenTelemetry starter does not do on its own.
 *
 * <p>Metrics and traces need nothing here: the starter exports both over OTLP
 * when {@code OTLP_ENABLED=true}. Logs are the exception. Boot creates the
 * OTLP log exporter, but log lines only reach it through an OpenTelemetry
 * appender attached to Logback, which is what {@link #openTelemetryLogAppender}
 * does. Each exported line carries the trace and span id of the request that
 * wrote it, so Grafana can jump from a trace to its logs and back.
 */
@Configuration(proxyBeanMethods = false)
public class ObservabilityConfiguration {

    static final String APPENDER_NAME = "OTEL";

    @Bean
    LogAppenderInstaller openTelemetryLogAppender(OpenTelemetry openTelemetry) {
        return new LogAppenderInstaller(openTelemetry);
    }

    /**
     * Leaves health checks out of traces and request metrics. Render probes
     * {@code /actuator/health} around the clock; recording each probe would
     * spend most of the free tracing allowance on requests nobody looks at.
     */
    @Bean
    ObservationPredicate skipActuatorRequests() {
        return (name, context) -> !(context instanceof ServerRequestObservationContext server
                && server.getCarrier().getRequestURI().startsWith("/actuator"));
    }

    /** Adds the appender to the root logger once, and takes it off again on shutdown. */
    static final class LogAppenderInstaller implements InitializingBean, DisposableBean {

        private final OpenTelemetry openTelemetry;
        private OpenTelemetryAppender appender;

        LogAppenderInstaller(OpenTelemetry openTelemetry) {
            this.openTelemetry = openTelemetry;
        }

        @Override
        public void afterPropertiesSet() {
            if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext context)) {
                return;
            }
            Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            if (root.getAppender(APPENDER_NAME) != null) {
                return;
            }
            OpenTelemetryAppender otel = new OpenTelemetryAppender();
            otel.setName(APPENDER_NAME);
            otel.setContext(context);
            otel.setOpenTelemetry(openTelemetry);
            otel.start();
            root.addAppender(otel);
            this.appender = otel;
        }

        @Override
        public void destroy() {
            if (appender != null
                    && LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
                context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).detachAppender(appender);
                appender.stop();
            }
        }
    }
}
