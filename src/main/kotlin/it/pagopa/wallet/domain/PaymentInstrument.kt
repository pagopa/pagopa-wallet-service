package it.pagopa.wallet.domain

/**
 * A Payment Instrument (e.g. credit card, bank account, PayPal account, ...).
 *
 * This class holds a remote identifier to a payment instrument stored inside the payment gateway
 * and an access token associated to the specific payment instrument.
 */
data class PaymentInstrument(val id: PaymentInstrumentId, val securityToken: String)
