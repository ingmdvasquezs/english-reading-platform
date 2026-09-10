ALTER TABLE users
    ADD COLUMN alias VARCHAR(50),
    ADD COLUMN age INTEGER,
    ADD COLUMN native_language VARCHAR(10),
    ADD COLUMN learning_language VARCHAR(10) NOT NULL DEFAULT 'en',
    ADD CONSTRAINT ck_users_age CHECK (age IS NULL OR age BETWEEN 5 AND 120),
    ADD CONSTRAINT ck_users_native_language CHECK (
        native_language IS NULL OR native_language ~ '^[a-z]{2,3}(-[A-Z]{2,3})?$'
    ),
    ADD CONSTRAINT ck_users_learning_language CHECK (learning_language = 'en');

CREATE UNIQUE INDEX uk_users_alias_normalized
    ON users (lower(alias))
    WHERE alias IS NOT NULL;
