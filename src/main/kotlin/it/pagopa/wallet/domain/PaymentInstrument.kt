package it.pagopa.wallet.domain

/**
 * A Payment Instrument (e.g. credit card, bank account, PayPal account, ...).
 *
 * This class holds a remote identifier to a payment instrument stored inside the payment gateway.
 */
data class PaymentInstrument(val id: PaymentInstrumentId)
