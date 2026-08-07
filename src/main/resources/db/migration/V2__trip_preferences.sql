-- What riders actually ask before booking: will my bag fit, and can I bring the
-- dog. These lived in the free-text notes field, which meant they could not be
-- filtered, could not be shown on a card, and were often just missing.

-- Defaults are the common case, so existing rows land on sensible values rather
-- than needing a backfill pass: most cars take a cabin bag, and the polite
-- default for a stranger's car is no smoking and no pets.
ALTER TABLE ride
    ADD COLUMN bag_size        TEXT    NOT NULL DEFAULT 'SMALL',
    ADD COLUMN smoking_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN pets_allowed    BOOLEAN NOT NULL DEFAULT FALSE;

-- The application maps this to an enum; the constraint stops a bad write from
-- persisting a value no code can read back.
ALTER TABLE ride
    ADD CONSTRAINT ride_bag_size_known CHECK (bag_size IN ('NONE', 'SMALL', 'LARGE'));
