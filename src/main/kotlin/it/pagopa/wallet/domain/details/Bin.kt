package it.pagopa.wallet.domain.details

data class Bin(val bin: String) {
    init {
        require(Regex("[0-9]{6}").matchEntire(bin) != null) { "Invalid bin format" }
    }
}
