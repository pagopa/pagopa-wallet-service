package it.pagopa.wallet.domain.wallets.details

data class Bin(val bin: String) {
    init {
        require(Regex("[0-9]{8}").matchEntire(bin) != null) { "Invalid bin format" }
    }
}
