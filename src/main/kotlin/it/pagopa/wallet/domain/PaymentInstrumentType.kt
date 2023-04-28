package it.pagopa.wallet.domain

/**
 * Payment Instrument Type enum.
 *
 * This enum class define a PaymentInstrument type from the following allowed values:
 * - CARDS
 */
enum class PaymentInstrumentType(val value: String) {
    CARDS("CARDS")
}
