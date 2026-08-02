package com.saryom.rideservice.events;

/**
 * Publishes a domain event to a named topic. Abstraction (DIP) so services depend
 * on this rather than Spring Cloud Stream directly, and tests can mock it.
 */
public interface DomainEventPublisher {

    void publish(String topic, Object event);
}
