package it.pagopa.wallet.domain.wallets.details

data class MaskedPan(val maskedPan: String) {
    init {
        require(Regex("[0-9]{8}[*]{4}[0-9]{4}").matchEntire(maskedPan) != null) {
            "Invalid masked pan format"
        }
    }
}
