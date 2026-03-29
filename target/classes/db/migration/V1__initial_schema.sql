-- V1__initial_schema.sql
-- Payment System Initial Schema

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- Users table
CREATE TABLE users (
       id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
       email            VARCHAR(255) NOT NULL UNIQUE,
       password         VARCHAR(255) NOT NULL,
       first_name       VARCHAR(255) NOT NULL,
       last_name        VARCHAR(255) NOT NULL,
       other_name       VARCHAR(255) NOT NULL,
       enabled          BOOLEAN NOT NULL DEFAULT TRUE,
       account_non_locked BOOLEAN NOT NULL DEFAULT TRUE,
       account_non_expired BOOLEAN NOT NULL DEFAULT TRUE,
       credentials_non_expired BOOLEAN NOT NULL DEFAULT TRUE,
       created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
       updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_users_id ON users(id);
CREATE INDEX idx_users_email ON users(email);

-- User roles table (many roles per user)
CREATE TABLE user_roles (
                            user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                            role    VARCHAR(50) NOT NULL,
                            PRIMARY KEY (user_id, role)
);

-- ─── Accounts ─────────────────────────────────────────────────────────────────

-- Sequence for accountNumber
CREATE SEQUENCE account_number_seq
    START 1050360080
    INCREMENT 1
    MINVALUE 1050360080
    MAXVALUE 1999999999
    CACHE 1;

-- Accounts table
CREATE TABLE accounts (
      id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
      user_id           UUID NOT NULL,
      status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
          CHECK (status IN ('ACTIVE','SUSPENDED','CLOSED','UNDER_REVIEW')),
      account_number    BIGINT NOT NULL DEFAULT nextval('account_number_seq'),
      currency          VARCHAR(10) NOT NULL,
      account_type      VARCHAR(20) NOT NULL,
      balance           NUMERIC(19,4) NOT NULL DEFAULT 0,
      available_balance NUMERIC(19,4) NOT NULL DEFAULT 0,
      kyc_verified      BOOLEAN NOT NULL DEFAULT FALSE,
      version           BIGINT NOT NULL DEFAULT 0,
      created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Constraints
      CONSTRAINT accounts_balance_non_negative CHECK (balance >= 0),
      CONSTRAINT accounts_available_balance_non_negative CHECK (available_balance >= 0),
      CONSTRAINT accounts_available_le_balance CHECK (available_balance <= balance),
      CONSTRAINT uk_user_account_type UNIQUE(user_id, account_type)
);

-- Indexes
CREATE INDEX idx_accounts_user_id ON accounts(user_id);
CREATE INDEX idx_accounts_accountNumber ON accounts(account_number);
CREATE INDEX idx_accounts_status ON accounts(status);

-- ─── Payments ─────────────────────────────────────────────────────────────────
CREATE TABLE payments (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    reference_id     VARCHAR(50)  NOT NULL UNIQUE,
    amount           NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency         VARCHAR(10)  NOT NULL,
    status           VARCHAR(30)  NOT NULL DEFAULT 'PENDING'
                         CHECK (status IN (
                             'PENDING','PROCESSING','COMPLETED','FAILED',
                             'CANCELLED','REFUNDED','PARTIALLY_REFUNDED','DISPUTED'
                         )),
    method           VARCHAR(30)  NOT NULL,
    description      VARCHAR(500),
    metadata         TEXT,
    idempotency_key  VARCHAR(128) NOT NULL UNIQUE,
    processing_fee   NUMERIC(19,4) NOT NULL DEFAULT 0,
    net_amount       NUMERIC(19,4) NOT NULL DEFAULT 0,
    risk_score       DOUBLE PRECISION,
    failure_reason   VARCHAR(500),
    completed_at     TIMESTAMPTZ,
    version          BIGINT NOT NULL DEFAULT 0,
    sender_id        UUID NOT NULL REFERENCES accounts(id),
    recipient_id     UUID NOT NULL REFERENCES accounts(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT payments_different_accounts CHECK (sender_id != recipient_id),
    CONSTRAINT payments_net_amount_positive CHECK (net_amount >= 0)
);

CREATE INDEX idx_payments_reference_id   ON payments(reference_id);
CREATE INDEX idx_payments_idempotency    ON payments(idempotency_key);
CREATE INDEX idx_payments_sender         ON payments(sender_id);
CREATE INDEX idx_payments_recipient      ON payments(recipient_id);
CREATE INDEX idx_payments_status         ON payments(status);
CREATE INDEX idx_payments_created_at     ON payments(created_at DESC);
CREATE INDEX idx_payments_sender_status  ON payments(sender_id, status, created_at DESC);

-- ─── Transactions ─────────────────────────────────────────────────────────────
CREATE TABLE transactions (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    type           VARCHAR(20) NOT NULL
                       CHECK (type IN ('PAYMENT','REFUND','CHARGEBACK','ADJUSTMENT','FEE')),
    amount         NUMERIC(19,4) NOT NULL,
    currency       VARCHAR(10)  NOT NULL,
    balance_before NUMERIC(19,4) NOT NULL,
    balance_after  NUMERIC(19,4) NOT NULL,
    description    VARCHAR(500),
    payment_id     UUID NOT NULL REFERENCES payments(id),
    account_id     UUID NOT NULL REFERENCES accounts(id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transactions_payment    ON transactions(payment_id);
CREATE INDEX idx_transactions_account    ON transactions(account_id);
CREATE INDEX idx_transactions_created_at ON transactions(created_at DESC);

-- ─── Refunds ──────────────────────────────────────────────────────────────────
CREATE TABLE refunds (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    amount           NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency         VARCHAR(10)  NOT NULL,
    reason           VARCHAR(500) NOT NULL,
    status           VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    idempotency_key  VARCHAR(128) NOT NULL UNIQUE,
    processed_at     TIMESTAMPTZ,
    payment_id       UUID NOT NULL REFERENCES payments(id),
    initiated_by_id  UUID NOT NULL REFERENCES accounts(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refunds_payment      ON refunds(payment_id);
CREATE INDEX idx_refunds_idempotency  ON refunds(idempotency_key);

-- ─── Stored Payment Methods ───────────────────────────────────────────────────
CREATE TABLE stored_payment_methods (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    type             VARCHAR(30)  NOT NULL,
    token_encrypted  TEXT NOT NULL,
    last4            VARCHAR(4),
    brand            VARCHAR(30),
    expiry_month     INT CHECK (expiry_month BETWEEN 1 AND 12),
    expiry_year      INT CHECK (expiry_year >= 2024),
    is_default       BOOLEAN NOT NULL DEFAULT FALSE,
    account_id       UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_spm_account ON stored_payment_methods(account_id);

-- ─── Audit Logs ───────────────────────────────────────────────────────────────
CREATE TABLE audit_logs (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50)  NOT NULL,
    entity_id   UUID NOT NULL,
    actor_id    UUID NOT NULL,
    changes     TEXT,
    ip_address  VARCHAR(45),
    user_agent  VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_entity     ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_actor      ON audit_logs(actor_id);
CREATE INDEX idx_audit_created_at ON audit_logs(created_at DESC);

-- ─── Auto-update updated_at ───────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_accounts_updated_at
    BEFORE UPDATE ON accounts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_payments_updated_at
    BEFORE UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
