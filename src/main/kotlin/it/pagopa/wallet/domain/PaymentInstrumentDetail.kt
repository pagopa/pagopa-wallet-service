package it.pagopa.wallet.domain

import java.util.*

/**
 * A Payment Instrument (e.g. credit card, bank account, PayPal account, ...).
 *
 * This class holds a remote identifier to a payment instrument stored inside the payment gateway
 * and an access token associated to the specific payment instrument.
 */
data class PaymentInstrumentDetail(val bin: String, val maskedPan: String, val expiryDate: Date)
