-- V2__seed_data.sql
-- Development seed data — NOT for production

INSERT INTO accounts (id, external_id, email, full_name, status, currency, balance, available_balance, kyc_verified)
VALUES
    ('a0000000-0000-0000-0000-000000000001', 'EXT-001', 'alice@example.com',
     'Alice Johnson', 'ACTIVE', 'USD', 10000.0000, 10000.0000, TRUE),
    ('a0000000-0000-0000-0000-000000000002', 'EXT-002', 'bob@example.com',
     'Bob Smith',    'ACTIVE', 'USD',  5000.0000,  5000.0000, TRUE),
    ('a0000000-0000-0000-0000-000000000003', 'EXT-003', 'carol@example.com',
     'Carol White',  'ACTIVE', 'EUR',  8000.0000,  8000.0000, FALSE)
ON CONFLICT DO NOTHING;
