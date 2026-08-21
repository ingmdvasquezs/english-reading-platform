ALTER TABLE users
    ADD COLUMN password_hash VARCHAR(100);

UPDATE users
SET email = lower(trim(email)),
    -- Legacy users cannot authenticate until a real registration/password flow provisions credentials.
    password_hash = '{bcrypt}$2a$10$invalidlegacycredentialinvalidlegacycredentialinv';

ALTER TABLE users
    ALTER COLUMN password_hash SET NOT NULL;

ALTER TABLE users
    DROP CONSTRAINT uk_users_email;

CREATE UNIQUE INDEX uk_users_email_normalized
    ON users (lower(email));
