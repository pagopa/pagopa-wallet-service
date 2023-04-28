package it.pagopa.wallet.domain.details

import it.pagopa.generated.wallet.model.TypeDto

/** Extensible interface to handle multiple wallet details typologies, such as CARDS */
interface WalletDetails {

    fun type(): TypeDto
}
