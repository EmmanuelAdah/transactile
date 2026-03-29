-- V2__seed_data.sql
-- Development seed data — NOT for production

INSERT INTO accounts
(id, user_id, account_number, status, currency, account_type, balance, available_balance, kyc_verified)
VALUES
    ('a0000000-0000-0000-0000-000000000001', 'u0000000-0000-0000-0000-000000000001', nextval('account_number_seq'),
     'ACTIVE', 'USD', 'SAVINGS', 10000.0000, 10000.0000, TRUE),
    ('a0000000-0000-0000-0000-000000000002', 'u0000000-0000-0000-0000-000000000002', nextval('account_number_seq'),
     'ACTIVE', 'USD', 'CHECKING', 5000.0000, 5000.0000, TRUE),
    ('a0000000-0000-0000-0000-000000000003', 'u0000000-0000-0000-0000-000000000003', nextval('account_number_seq'),
     'ACTIVE', 'EUR', 'SAVINGS', 8000.0000, 8000.0000, FALSE)
ON CONFLICT DO NOTHING;
