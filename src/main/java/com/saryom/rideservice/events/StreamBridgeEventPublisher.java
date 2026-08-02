package com.saryom.rideservice.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

/**
 * Publishes events through Spring Cloud Stream's {@link StreamBridge}. The binding
 * (destination) name equals the topic name, so the broker is a config-time binder
 * choice (Kafka or RabbitMQ) with no code change.
 */
@Component
public class StreamBridgeEventPublisher implements DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(StreamBridgeEventPublisher.class);

    private final StreamBridge streamBridge;

    public StreamBridgeEventPublisher(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    /**
     * Sends the event, logging loudly if the binder refuses it.
     *
     * <p>{@code StreamBridge.send} reports failure by returning false rather than
     * throwing, so discarding the result drops the event with no trace at all —
     * a sold listing that silently notifies nobody looks identical to one that
     * was never sold. We deliberately do not throw: the business transaction has
     * already succeeded and failing the caller's request would be the worse
     * outcome. Making the loss visible is the point; a transactional outbox is
     * the real fix if delivery ever needs to be guaranteed.
     */
    @Override
    public void publish(String topic, Object event) {
        boolean delivered = streamBridge.send(topic, event);
        if (!delivered) {
            log.error("Dropped event on topic {} — binder rejected the send: {}", topic, event);
        }
    }
}
