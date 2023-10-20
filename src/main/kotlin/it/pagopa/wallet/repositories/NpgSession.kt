package it.pagopa.wallet.repositories

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceCreator
import org.springframework.lang.NonNull

class NpgSession
@PersistenceCreator
constructor(
    @NonNull @Id val orderId: String,
    @NonNull val sessionId: String,
    @NonNull val securityToken: String,
    @NonNull val walletId: String,
) {}
