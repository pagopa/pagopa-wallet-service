package it.pagopa.wallet.domain.details

import it.pagopa.generated.wallet.model.WalletCardDetailsDto

/** Data class that maps WalletDetails for CARD instrument type */
data class CardDetails(
    val bin: String,
    val maskedPan: String,
    val expiryDate: String,
    val brand: WalletCardDetailsDto.BrandEnum,
    val holderName: String
) : WalletDetails {
    init {
        require(Regex("[0-9]{6}").matchEntire(bin) != null) { "Invalid bin format" }
        require(Regex("[0-9]{6}[*]{6}[0-9]{4}").matchEntire(maskedPan) != null) {
            "Invalid masked pan format"
        }
        require(Regex("^\\d{6}$").matchEntire(expiryDate) != null) { "Invalid expiry date format" }
    }
}
