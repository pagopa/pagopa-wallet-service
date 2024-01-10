package it.pagopa.wallet.domain.wallets.details

import it.pagopa.generated.wallet.model.WalletCardDetailsDto
import it.pagopa.wallet.documents.wallets.details.CardDetails

/** Data class that maps WalletDetails for CARD instrument type */
data class CardDetails(
    val bin: Bin,
    val maskedPan: MaskedPan,
    val expiryDate: ExpiryDate,
    val brand: WalletCardDetailsDto.BrandEnum,
    val holder: CardHolderName
) : WalletDetails<CardDetails> {
    override val type: WalletDetailsType
        get() = WalletDetailsType.CARDS

    override fun toDocument(): CardDetails =
        CardDetails(
            this.type.name,
            this.bin.bin,
            this.maskedPan.maskedPan,
            this.expiryDate.expDate,
            this.brand.name,
            this.holder.holderName
        )
}
