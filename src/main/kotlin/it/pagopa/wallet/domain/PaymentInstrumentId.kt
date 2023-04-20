package it.pagopa.wallet.domain

import java.util.UUID

/**
 * A payment instrument identifier.
 *
 * This class is a reference to remote data pertaining to a user payment instrument (e.g. for credit
 * cards this will include the cardholder, PAN, CVV and expiration date).
 *
 * The Wallet is aware only of this stable identifier which is used to communicate with the Payment
 * Gateway.
 *
 * This class reifies what is described in
 * [Nexi's NPG](https://developer.nexigroup.com/it/api/post-orders-hpp) as a `contractId`.
 */
data class PaymentInstrumentId(val value: UUID)
