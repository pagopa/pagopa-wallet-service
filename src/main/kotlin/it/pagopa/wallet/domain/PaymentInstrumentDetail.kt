package it.pagopa.wallet.domain

import java.util.*

/**
 * A Payment Instrument Detail data.
 *
 * This class holds information about the payment instrument. bin: first 6 chars of the card number
 * maskedPan: first 6 chars + last 4 chars of the card number expiryDate: expiry date of the card
 *
 * @throws IllegalArgumentException when bin/maskedPan are not well formatted
 */
data class PaymentInstrumentDetail(val bin: String, val maskedPan: String, val expiryDate: String) {
    init {
        require(Regex("[0-9]{6}").matchEntire(bin) != null) { "Invalid bin format" }
        require(Regex("[0-9]{6}[*]{6}[0-9]{4}").matchEntire(maskedPan) != null) {
            "Invalid masked pan format"
        }
        require(Regex("^\\d{6}$").matchEntire(expiryDate) != null) { "Invalid expiry date format" }
    }
}
