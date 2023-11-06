package it.pagopa.wallet.domain.details

data class ExpiryDate(val expDate: String) {
    init {
        require(Regex("^\\d{2}/\\d{2}$").matchEntire(expDate) != null) {
            "Invalid expiry date format"
        }
    }
}
