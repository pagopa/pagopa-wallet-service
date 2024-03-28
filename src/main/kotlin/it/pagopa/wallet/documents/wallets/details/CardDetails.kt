package it.pagopa.wallet.documents.wallets.details

import it.pagopa.wallet.domain.wallets.details.Bin
import it.pagopa.wallet.domain.wallets.details.ExpiryDate
import it.pagopa.wallet.domain.wallets.details.LastFourDigits
import it.pagopa.wallet.domain.wallets.details.PaymentInstrumentGatewayId

data class CardDetails(
    val type: String,
    val bin: String,
    val lastFourDigits: String,
    val expiryDate: String,
    val brand: String,
    val paymentInstrumentGatewayId: String
) : WalletDetails<CardDetails> {
    override fun toDomain() =
        it.pagopa.wallet.domain.wallets.details.CardDetails(
            Bin(bin),
            LastFourDigits(lastFourDigits),
            ExpiryDate(expiryDate),
            brand,
            PaymentInstrumentGatewayId(paymentInstrumentGatewayId)
        )
}
