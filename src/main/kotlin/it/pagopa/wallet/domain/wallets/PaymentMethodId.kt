package it.pagopa.wallet.domain.wallets

import java.util.*

/**
 * A payment method identifier.
 *
 * This class is a reference to remote data pertaining to a user payment method (e.g. for credit
 * cards this will include the cardholder, PAN, CVV and expiration date).
 *
 * The Wallet is aware only of this stable identifier which is used to communicate with the
 * payment-method-service.
 */
data class PaymentMethodId(val value: UUID)
