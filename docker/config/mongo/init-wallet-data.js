db = db.getSiblingDB('wallet');

// ensure clean state
db.wallets.drop();
db.getCollection("payment-wallets").drop();

// insert into payment-wallets collection
db.getCollection("payment-wallets").insertMany([
    {
        "_id": "550e8400-e29b-41d4-a716-446655440001",
        "userId": "00000000-0000-0000-0000-000000000001",
        "status": "VALIDATED",
        "paymentMethodId": "f25399bf-c56f-4bd2-adc9-7aef87410609",
        "contractId": "W1763480342163asdf",
        "validationOperationResult": "EXECUTED",
        "onboardingChannel": "IO",
        "version": 3,
        "creationDate": new Date("2025-01-15T10:00:00Z"),
        "updateDate": new Date("2025-01-15T10:00:00Z"),
        "details": null,
        "applications": [],
        "clients": {},
        "_class": "it.pagopa.wallet.documents.wallets.Wallet"
    },
    {
        "_id": "550e8400-e29b-41d4-a716-446655440002",
        "userId": "00000000-0000-0000-0000-000000000001",
        "status": "ERROR",
        "paymentMethodId": "f25399bf-c56f-4bd2-adc9-7aef87410609",
        "errorReason": "Wallet expired.",
        "onboardingChannel": "IO",
        "version": 3,
        "creationDate": new Date("2025-01-14T11:30:00Z"),
        "updateDate": new Date("2025-01-14T11:30:00Z"),
        "applications": [],
        "clients": {},
        "_class": "it.pagopa.wallet.documents.wallets.Wallet"
    }
]);