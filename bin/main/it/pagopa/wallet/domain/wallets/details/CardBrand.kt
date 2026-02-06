package it.pagopa.wallet.domain.wallets.details

class CardBrand(rawValue: String) {

    val value: String =
        when (rawValue) {
            "MC" -> "MASTERCARD"
            "MAE" -> "MAESTRO"
            else -> rawValue
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        return value == (other as CardBrand).value
    }

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "CardBrand(value='$value')"
}
