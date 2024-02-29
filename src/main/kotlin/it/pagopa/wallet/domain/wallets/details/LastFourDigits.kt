package it.pagopa.wallet.domain.wallets.details

data class LastFourDigits(val lastFourDigits: String) {
    init {
        require(Regex("[0-9]{4}").matchEntire(lastFourDigits) != null) {
            "Invalid last four digits format"
        }
    }
}
