package it.pagopa.wallet.documents.wallets

import it.pagopa.wallet.domain.wallets.WalletId
import java.time.Instant
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.annotation.Version

open class UuidIdentifiedEntity(@Id var id: WalletId, @Version var version: Number? = null) {

    @CreatedDate lateinit var creationDate: Instant

    @LastModifiedDate lateinit var updateDate: Instant
}
