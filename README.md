# saryom-ride-service

Ride sharing for the Saryom marketplace — the "Share Rides, Share Costs"
vertical. Drivers post a trip with spare seats; riders browse nearby departures
and book a seat.

- **Stack:** Java 25, Spring Boot 4.0.x (MVC), Spring Data JPA + Flyway (Postgres
  schema `rides`), Spring Security (Firebase ID tokens), Spring Cloud Stream
  (Kafka/RabbitMQ), springdoc OpenAPI.
- **Port:** `8087`
- **Docs:** Swagger UI at `/swagger-ui.html`, spec at `/v3/api-docs`.

## Domain

Two entities.

`Ride` is the aggregate root. Seat arithmetic and the lifecycle live on the
entity, so invalid transitions are impossible from outside:

```
OPEN --book(last seat)--> FULL --complete--> COMPLETED
  |                         |
  |                         +--release seats--> OPEN
  +--cancel--> CANCELLED   (also reachable from FULL)
```

`FULL` is derived from the seat count, never set by a caller — it simply means
"no seats left".

`Booking` is one rider's claim. It is a separate entity rather than a counter on
the ride because the driver needs to know *who* is coming and a rider needs their
trips listed back to them; a bare count could do neither. Cancelling sets a
status rather than deleting the row, so book/cancel/rebook leaves a history.

### Overbooking is the risk this service exists to get right

Two riders taking the last seat both read `seats_available = 1` and both pass the
domain check. Without protection the second write silently overwrites the first,
and the driver finds out at the kerb. A seat cannot be split, so this is worse
than the equivalent race in food-service reservations.

| Layer | Guard |
|-------|-------|
| Entity | `@Version` on `Ride` — the loser's commit fails and the API returns 409 |
| Schema | `CHECK (seats_available >= 0 AND seats_available <= seats_total)` |
| Schema | Partial unique index: one `CONFIRMED` booking per rider per ride, which cannot be raced the way an application-level check can |

## API

Public browse; everything else needs `Authorization: Bearer <Firebase ID token>`
(or `dev:<uid>` under the `dev` profile).

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| GET | `/api/rides` | public | Browse (`q`, `seats`, `sort`, `lat`/`lng`/`radiusMiles`, `page`, `size`) |
| GET | `/api/rides/{id}` | public | Ride detail |
| GET | `/api/rides/mine` | ✓ | Rides I'm driving |
| GET | `/api/rides/booked` | ✓ | Rides I have a seat on |
| POST | `/api/rides` | ✓ | Post a ride |
| PATCH | `/api/rides/{id}` | driver | Edit details |
| POST | `/api/rides/{id}/bookings` | ✓ | Book seats |
| DELETE | `/api/rides/{id}/bookings/{bookingId}` | rider/driver | Cancel a booking |
| POST | `/api/rides/{id}/complete` | driver | Mark the trip done |
| DELETE | `/api/rides/{id}` | driver | Cancel the ride |

`q` matches **either** endpoint — "Chicago" finds rides leaving there and rides
heading there.

Seat capacity is deliberately **not** editable: lowering it below what riders
have already booked would strand someone silently. Changing capacity means
cancelling and reposting, which at least tells them.

The rider list on `GET /api/rides/{id}` is returned **only** to the driver. A
rider has no business seeing who else is in the car before the trip.

Browsing with `lat`/`lng` filters to a radius of the **pickup** point and returns
a distance per card. That sort happens in memory, because it depends on where the
caller is.

## Events (Spring Cloud Stream)

| Topic | When |
|-------|------|
| `ride.posted` | A ride is on offer |
| `ride.booked` | Seats taken — carries the driver, who is the one to notify |
| `ride.booking_cancelled` | A rider gave seats back; the trip is fine, the driver has a seat to fill |
| `ride.cancelled` | The driver called it off — carries **every** affected rider |

The last two are separate on purpose. One means "your trip is gone", the other
"you have a seat to fill". A single message would be wrong for whichever party
received it.

## Local run

```bash
# Postgres schema `rides` must exist (see saryom-db). Then:
mvn spring-boot:run -Dspring-boot.run.profiles=dev,rabbit
```

Under `dev`, tokens are `dev:<uid>` (stub verifier); with real Firebase, unset
the `dev` profile and provide `FIREBASE_SERVICE_ACCOUNT_JSON`.

## Tests

```bash
mvn verify
```

The suite runs on H2 with `ddl-auto=create-drop`, so the Flyway scripts never
execute there. `SchemaMigrationTest` runs the real migrations against Postgres
via Testcontainers with `ddl-auto=validate`, **in the `rides` schema rather than
`public`** — listing-service shipped a native query that resolved in `public` and
failed once deployed into its real schema, and a suite pinned to `public` cannot
catch that.

## Deploy (Render)

Needs `DB_URL` / `DB_USER` / `DB_PASSWORD` / `CLOUDAMQP_URL`, plus a `rides`
schema in Neon. The JDBC URL needs **both** schema settings:

```
jdbc:postgresql://<host>/neondb?sslmode=require&currentSchema=rides&options=-c%20search_path%3Drides
```

`currentSchema` is what Hibernate uses to qualify mapped entities; `options=-c
search_path` is what any native SQL resolves against. Omitting the second is what
broke listing-service in production.
