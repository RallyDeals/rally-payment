
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
     'ac8e30d2-0e2e-48f6-81ab-71367fb3dfb6',
     'cus_V5MJYTQRfsuc7C');

-- -------------------------------------------------------------------
-- Payment Methods (Including Success & All Standard Stripe Failures)
-- -------------------------------------------------------------------
INSERT INTO payment_methods
(id, user_id, type, token, is_default,
 card_brand, card_last4, card_exp_month, card_exp_year,
 card_fingerprint, version)
VALUES
    -- --- SUCCESSFUL CARDS (Pre-existing) ---

    -- User 1: Visa (Success)
    ('c21f969b-5779-4328-82d1-935104d49d94',
     '4dc618d7-290b-4eee-8cb5-6115e6085b6b',
     'CARD', 'pm_1U8iLl0uquxxwDMnGeD4YQY4', TRUE,
     'Visa', '4242', '12', '2029',
     'y9gJAW5rl4ecYQuX', 0),

    -- User 2: Mastercard (Success)
    ('a82d6b38-60d7-4b71-9b19-1a9e8f1bb54e',
     '60ce018b-400f-4363-a3c3-e8a85d36dce4',
     'CARD', 'pm_1U6sJG0uquxxwDMnILbncUpH', FALSE,
     'MasterCard', '4444', '10', '2030',
     'UXc1tp511YqTzhHD', 0),

    -- User 3: American Express (Success)
    ('e53f1201-9876-4321-b456-123456789abc',
     'ac8e30d2-0e2e-48f6-81ab-71367fb3dfb6',
     'CARD', 'pm_1U6sEo0uquxxwDMn4RnMNWwM', FALSE,
     'American Express', '0005', '10', '2030',
     'jSpwLdScyyLpeezw', 0),


    -- --- FAILURE / DECLINE TEST CARDS (Mapped to User 1) ---

    -- Generic Decline (pm_card_chargeDeclined)
    ('11111111-1111-4111-8111-111111111111',
     '4dc618d7-290b-4eee-8cb5-6115e6085b6b',
     'CARD', 'pm_card_chargeDeclined', FALSE,
     'Visa', '0002', '12', '2030',
     'fp_gen_decline_01', 0),

    -- Insufficient Funds (pm_card_chargeDeclinedInsufficientFunds)
    ('22222222-2222-4222-8222-222222222222',
     '4dc618d7-290b-4eee-8cb5-6115e6085b6b',
     'CARD', 'pm_card_chargeDeclinedInsufficientFunds', FALSE,
     'Visa', '9995', '12', '2030',
     'fp_insuff_funds_02', 0),

    -- Expired Card (pm_card_chargeDeclinedExpiredCard)
    ('33333333-3333-4333-8333-333333333333',
     '4dc618d7-290b-4eee-8cb5-6115e6085b6b',
     'CARD', 'pm_card_chargeDeclinedExpiredCard', FALSE,
     'Visa', '0069', '12', '2030',
     'fp_expired_card_03', 0),

    -- Incorrect CVC (pm_card_chargeDeclinedIncorrectCvc)
    ('44444444-4444-4444-8444-444444444444',
     '4dc618d7-290b-4eee-8cb5-6115e6085b6b',
     'CARD', 'pm_card_chargeDeclinedIncorrectCvc', FALSE,
     'Visa', '0127', '12', '2030',
     'fp_incorrect_cvc_04', 0),

    -- Processing Error (pm_card_chargeDeclinedProcessingError)
    ('55555555-5555-4555-8555-555555555555',
     '4dc618d7-290b-4eee-8cb5-6115e6085b6b',
     'CARD', 'pm_card_chargeDeclinedProcessingError', FALSE,
     'Visa', '0119', '12', '2030',
     'fp_proc_error_05', 0),

    -- Fraudulent Decline (pm_card_chargeDeclinedFraudulent)
    ('66666666-6666-4666-8666-666666666666',
     '4dc618d7-290b-4eee-8cb5-6115e6085b6b',
     'CARD', 'pm_card_chargeDeclinedFraudulent', FALSE,
     'Visa', '0019', '12', '2030',
     'fp_fraud_decline_06', 0);