package it.pagopa.wallet.domain.details

import it.pagopa.generated.wallet.model.WalletCardDetailsDto

/** Data class that maps WalletDetails for CARD instrument type */
data class CardDetails(
    val bin: Bin,
    val maskedPan: MaskedPan,
    val expiryDate: ExpiryDate,
    val brand: WalletCardDetailsDto.BrandEnum,
    val holder: CardHolderName
) : WalletDetails {
    override val type: WalletDetailsType
        get() = WalletDetailsType.CARDS
}
