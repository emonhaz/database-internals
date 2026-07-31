-- Setup for AirlineCheckin demo (PostgreSQL).
-- Example:
--   createdb shard0
--   psql -d shard0 -f schema.sql

DROP TABLE IF EXISTS seats;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
    id   SERIAL PRIMARY KEY,
    name TEXT NOT NULL
);

CREATE TABLE seats (
    id      SERIAL PRIMARY KEY,
    name    TEXT NOT NULL UNIQUE,
    user_id INT REFERENCES users (id)
);

INSERT INTO users (name) VALUES
    ('Alice'), ('Bob'), ('Carol'), ('Dave'),
    ('Eve'), ('Frank'), ('Grace'), ('Heidi');

INSERT INTO seats (name) VALUES
    ('1A'), ('1B'), ('1C'), ('1D'),
    ('2A'), ('2B'), ('2C'), ('2D'),
    ('3A'), ('3B');

-- Reset between runs:
-- UPDATE seats SET user_id = NULL;
