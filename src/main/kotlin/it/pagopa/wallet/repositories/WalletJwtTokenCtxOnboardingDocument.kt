package it.pagopa.wallet.repositories

import java.time.OffsetDateTime
import lombok.Data
import org.springframework.data.annotation.Id
import org.springframework.lang.NonNull

@Data
class WalletJwtTokenCtxOnboardingDocument(@NonNull @Id val id: String, val jwtToken: String) {

    constructor(id: String) : this(id, OffsetDateTime.now().toString())
}
