package it.pagopa.wallet.documents.wallets

import it.pagopa.wallet.domain.wallets.WalletId
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.annotation.Version
import java.time.Instant

abstract class UuidIdentifiedEntity {
    @Id
    var id: WalletId? = null

    @CreatedDate
    lateinit var creationDate: Instant

    @LastModifiedDate
    lateinit var updateDate: Instant

    @Version
    var version: Long? = null

    constructor() {}

    constructor(id: WalletId, creationDate: Instant, updateDate: Instant) {
        this.id = id
        this.creationDate
        this.updateDate
    }
}
