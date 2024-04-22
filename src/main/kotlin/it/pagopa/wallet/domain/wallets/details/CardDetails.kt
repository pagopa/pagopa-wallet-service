package it.pagopa.wallet.domain.wallets.details

import it.pagopa.wallet.documents.wallets.details.CardDetails

/** Data class that maps WalletDetails for CARD instrument type */
data class CardDetails(
    val bin: Bin,
    val lastFourDigits: LastFourDigits,
    val expiryDate: ExpiryDate,
    val brand: CardBrand,
    val paymentInstrumentGatewayId: PaymentInstrumentGatewayId
) : WalletDetails<CardDetails> {
    override val type: WalletDetailsType
        get() = WalletDetailsType.CARDS

    override fun toDocument(): CardDetails =
        CardDetails(
            this.type.name,
            this.bin.bin,
            this.lastFourDigits.lastFourDigits,
            this.expiryDate.expDate,
            this.brand.value,
            this.paymentInstrumentGatewayId.paymentInstrumentGatewayId
        )
}
