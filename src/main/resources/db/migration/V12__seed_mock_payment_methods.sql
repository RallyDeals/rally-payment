-- ===================================================================
-- Seed data: mocked payment profiles + payment methods (dev/test only)
-- 3 profiles, 3 payment methods.
-- ===================================================================

-- -------------------------------------------------------------------
-- Payment Profiles
-- -------------------------------------------------------------------
INSERT INTO payment_profiles (id, user_id, stripe_customer_id)
VALUES
    -- Profile 1: Standard Visa
    ('f47ac10b-58cc-4372-a567-0e02b2c3d479',
     '4dc618d7-290b-4eee-8cb5-6115e6085b6b',
     'cus_V5MJtno0eFOnej'),

    -- Profile 2: Mastercard
    ('9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d',
     '60ce018b-400f-4363-a3c3-e8a85d36dce4',
     'cus_V5MJFBPtzeCB98'),

    -- Profile 3: American Express
    ('7c9e6679-7425-40de-944b-e07fc1f90ae7',
     'a85dbbd6-a233-4437-a431-48357047e487',
     'cus_V5MJYTQRfsuc7C');

-- -------------------------------------------------------------------
-- Payment Methods
-- -------------------------------------------------------------------
INSERT INTO payment_methods
(id, user_id, type, token, is_default,
 card_brand, card_last4, card_exp_month, card_exp_year,
 card_fingerprint, version)
VALUES
    -- User 1: Visa
    ('c21f969b-5779-4328-82d1-935104d49d94',
     '4dc618d7-290b-4eee-8cb5-6115e6085b6b',
     'CARD', 'pm_1U70vP0uquxxwDMnuBy27n0g', TRUE,
     'Visa', '4242', '12', '2029',
     'y9gJAW5rl4ecYQuX', 0),

    -- User 2: Mastercard (from screenshot)
    ('a82d6b38-60d7-4b71-9b19-1a9e8f1bb54e',
     '60ce018b-400f-4363-a3c3-e8a85d36dce4',
     'CARD', 'pm_1U6sJG0uquxxwDMnILbncUpH', TRUE,
     'MasterCard', '4444', '10', '2030',
     'UXc1tp511YqTzhHD', 0),

    -- User 3: American Express (from screenshot)
    ('e53f1201-9876-4321-b456-123456789abc',
     'a85dbbd6-a233-4437-a431-48357047e487',
     'CARD', 'pm_1U6sEo0uquxxwDMn4RnMNWwM', TRUE,
     'American Express', '0005', '10', '2030',
     'jSpwLdScyyLpeezw', 0);