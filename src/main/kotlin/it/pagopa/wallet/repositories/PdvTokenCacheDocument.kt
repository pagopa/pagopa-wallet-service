package it.pagopa.wallet.repositories

import java.util.UUID
import org.springframework.data.annotation.Id
import org.springframework.lang.NonNull

data class PdvTokenCacheDocument(@NonNull @Id val hashedFiscalCode: String, val token: UUID)
