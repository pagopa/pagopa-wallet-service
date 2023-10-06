package it.pagopa.wallet.domain.details

import it.pagopa.generated.wallet.model.WalletCardDetailsDto

/** Data class that maps WalletDetails for CARD instrument type */
data class CardDetails(
    override val type: WalletDetailsType,
    val bin: Bin,
    val maskedPan: MaskedPan,
    val expiryDate: ExpiryDate,
    val brand: WalletCardDetailsDto.BrandEnum,
    val holder: CardHolderName
) : WalletDetails {
    init {
        require(Regex("[0-9]{6}").matchEntire(bin.bin) != null) { "Invalid bin format" }
        require(Regex("[0-9]{6}[*]{6}[0-9]{4}").matchEntire(maskedPan.maskedPan) != null) {
            "Invalid masked pan format"
        }
        require(Regex("^\\d{6}$").matchEntire(expiryDate.expDate) != null) {
            "Invalid expiry date format"
        }
    }
}
