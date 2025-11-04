package it.pagopa.wallet.repositories

import org.springframework.data.annotation.Id
import org.springframework.lang.NonNull

data class WalletJwtTokenCtxOnboardingDocument(
    @NonNull @Id val walletId: String,
    val jwtToken: String
)
