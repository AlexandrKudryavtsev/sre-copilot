-- Temporary owner for local development until authentication is implemented.
-- The password marker cannot be used as a login password hash.
INSERT INTO users (email, password_hash)
VALUES ('local@srecopilot.test', '!disabled-local-user')
ON CONFLICT (email) DO NOTHING;
