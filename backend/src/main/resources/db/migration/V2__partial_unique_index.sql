-- Database-level backstop: even if two transactions both pass the optimistic-lock
-- fast path (e.g. against stale application-level caching, or a bug in the service
-- layer), Postgres itself refuses a second CONFIRMED booking for the same
-- resource+slot. This is what BookingConcurrencyTest ultimately proves.
CREATE UNIQUE INDEX uq_bookings_resource_slot_confirmed
    ON bookings (resource_id, slot_start)
    WHERE status = 'CONFIRMED';
