package it.pagopa.wallet.domain.wallets.details

data class LastFourDigits(val lastFourDigits: String) {

    companion object {
        val regexLastFourDigits: Regex = Regex("[0-9]{4}")
    }

    init {
        require(regexLastFourDigits.matchEntire(lastFourDigits) != null) {
            "Invalid last four digits format"
        }
    }
}
