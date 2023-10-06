package it.pagopa.wallet.domain.details

data class ExpiryDate(val expDate: String) {
    init {
        require(Regex("^\\d{6}$").matchEntire(expDate) != null) { "Invalid expiry date format" }
    }
}
