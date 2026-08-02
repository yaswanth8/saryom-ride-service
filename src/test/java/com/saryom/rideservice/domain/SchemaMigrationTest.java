package com.saryom.rideservice.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the Flyway migrations against a real Postgres and checks the schema they
 * produce against the entities.
 *
 * <p>The rest of the suite runs on H2 with {@code ddl-auto: create-drop} and
 * {@code flyway.enabled: false}, so Hibernate builds the schema from the
 * entities and the migration scripts are never executed. A migration can name a
 * table that does not exist, or add a column no entity maps, while every test
 * passes — the failure then surfaces on deploy.
 *
 * <p>That is not hypothetical. chat-service shipped a migration saying
 * {@code ALTER TABLE message} when the table is {@code messages}; 45 tests
 * were green and the service failed to start on Render.
 *
 * <p>Runs in {@code rides}, the schema production uses — not {@code public}.
 * listing-service learned that the hard way: its suite pinned everything to
 * {@code public}, where unqualified names always resolve, and shipped a native
 * query that returned {@code relation "listing" does not exist} once deployed
 * into its real schema.
 *
 * <p>{@code ddl-auto=validate} is what makes this bite: Hibernate compares
 * every mapped entity against the migrated schema and fails startup on any
 * mismatch, so this test fails whenever a migration and an entity disagree.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.schemas=rides",
        "spring.flyway.default-schema=rides",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.default_schema=rides",
})
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("dev")
class SchemaMigrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private EntityManager entityManager;

    /**
     * Reaching this point already proves the migrations applied and Hibernate
     * validated every entity against the result — the context would not have
     * started otherwise. The count guards against a migration silently not being
     * picked up at all.
     */
    @Test
    void migrationsProduceTheSchemaTheEntitiesExpect() {
        Number applied = (Number) entityManager
                .createNativeQuery("select count(*) from rides.flyway_schema_history where success")
                .getSingleResult();
        assertThat(applied.intValue()).isGreaterThanOrEqualTo(1);
    }
}
