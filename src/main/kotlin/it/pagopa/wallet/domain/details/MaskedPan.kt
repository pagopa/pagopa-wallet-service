package it.pagopa.wallet.domain.details

data class MaskedPan(val maskedPan: String) {
    init {
        require(Regex("[0-9]{6}[*]{6}[0-9]{4}").matchEntire(maskedPan) != null) {
            "Invalid masked pan format"
        }
    }
}
