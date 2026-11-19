-- Accounts are optional: guests never touch the server. Only signed-in users have rows here.

CREATE TABLE app_user (
    id          UUID PRIMARY KEY,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ            -- account deletion requested; purged after 30 days
);

-- Sign-in identities: Apple / Google subject IDs, or a verified email for magic links.
CREATE TABLE user_identity (
    provider    TEXT NOT NULL CHECK (provider IN ('apple', 'google', 'email')),
    subject     TEXT NOT NULL,
    user_id     UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (provider, subject)
);

-- Rotating refresh tokens (only a hash is stored).
CREATE TABLE refresh_token (
    token_hash  TEXT PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    device_id   TEXT NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ
);
CREATE INDEX refresh_token_user ON refresh_token(user_id);

-- Append-only change log for sync (see proposal §8.4). Clients pull everything after their cursor (seq).
CREATE TABLE sync_change (
    seq          BIGSERIAL PRIMARY KEY,
    user_id      UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    device_id    TEXT NOT NULL,
    entity_type  TEXT NOT NULL,     -- bookmark, highlight, note, memory_item, plan_enrolment, ...
    entity_id    TEXT NOT NULL,
    op           TEXT NOT NULL CHECK (op IN ('upsert', 'delete')),
    fields       JSONB NOT NULL DEFAULT '{}'::jsonb,
    hlc          TEXT NOT NULL,     -- hybrid logical clock from the device, orders concurrent edits
    received_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX sync_change_user_seq ON sync_change(user_id, seq);

-- Defence in depth: the API scopes every query by user, and row-level security backs that up.
ALTER TABLE sync_change ENABLE ROW LEVEL SECURITY;
CREATE POLICY sync_change_owner ON sync_change
    USING (user_id::text = current_setting('app.user_id', true));

-- Admin platform accounts (separate from app users) and the audit log.
CREATE TABLE admin_user (
    id            UUID PRIMARY KEY,
    email         TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    totp_secret   TEXT,
    roles         TEXT[] NOT NULL DEFAULT '{}',   -- admin, editor, theology_reviewer, image_reviewer, publisher
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    admin_id    UUID REFERENCES admin_user(id),
    action      TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id   TEXT,
    detail      JSONB NOT NULL DEFAULT '{}'::jsonb   -- includes model, prompt hash and cost for AI events
);
