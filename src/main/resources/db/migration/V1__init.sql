-- Ride sharing: seat offers and the bookings against them.

CREATE TABLE ride (
    id                UUID PRIMARY KEY,
    -- Optimistic locking. Two riders taking the last seat both read
    -- seats_available = 1; without this the second write silently overwrites
    -- the first and the car is overbooked.
    version           BIGINT       NOT NULL DEFAULT 0,
    driver_id         TEXT         NOT NULL,
    origin_text       TEXT         NOT NULL,
    origin_lat        DOUBLE PRECISION,
    origin_lng        DOUBLE PRECISION,
    destination_text  TEXT         NOT NULL,
    destination_lat   DOUBLE PRECISION,
    destination_lng   DOUBLE PRECISION,
    depart_at         TIMESTAMPTZ  NOT NULL,
    seats_total       INTEGER      NOT NULL,
    seats_available   INTEGER      NOT NULL,
    price_per_seat    NUMERIC(10,2) NOT NULL,
    notes             TEXT,
    status            TEXT         NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,

    -- The database refuses to hold a state the domain treats as impossible,
    -- so a bug in the service cannot leave rows that no code can interpret.
    CONSTRAINT ride_seats_total_positive   CHECK (seats_total > 0),
    CONSTRAINT ride_seats_available_range  CHECK (seats_available >= 0 AND seats_available <= seats_total),
    CONSTRAINT ride_price_not_negative     CHECK (price_per_seat >= 0)
);

-- Browse is always "open rides departing after now", so the index leads with
-- the columns that filter and ends with the one that sorts.
CREATE INDEX idx_ride_status_depart ON ride (status, depart_at);
CREATE INDEX idx_ride_driver        ON ride (driver_id);
-- Partial: the geo path only ever looks at rides that have coordinates.
CREATE INDEX idx_ride_origin_coords ON ride (origin_lat, origin_lng)
    WHERE origin_lat IS NOT NULL AND origin_lng IS NOT NULL;

CREATE TABLE booking (
    id           UUID PRIMARY KEY,
    version      BIGINT      NOT NULL DEFAULT 0,
    ride_id      UUID        NOT NULL REFERENCES ride (id) ON DELETE CASCADE,
    rider_id     TEXT        NOT NULL,
    seats        INTEGER     NOT NULL,
    status       TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    cancelled_at TIMESTAMPTZ,

    CONSTRAINT booking_seats_positive CHECK (seats > 0)
);

CREATE INDEX idx_booking_ride  ON booking (ride_id, status);
CREATE INDEX idx_booking_rider ON booking (rider_id, created_at DESC);

-- One live booking per rider per ride. Enforced here rather than only in the
-- service because two concurrent requests can both pass an application-level
-- check; a partial unique index cannot be raced.
CREATE UNIQUE INDEX uq_booking_active_rider ON booking (ride_id, rider_id)
    WHERE status = 'CONFIRMED';
