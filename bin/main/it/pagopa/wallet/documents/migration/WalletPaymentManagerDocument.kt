package it.pagopa.wallet.documents.migration

import java.time.Instant
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document("wallets-migration-pm")
data class WalletPaymentManagerDocument(
    @Id var walletPmId: String,
    var walletId: String,
    var contractId: String,
    @CreatedDate var creationDate: Instant,
)
