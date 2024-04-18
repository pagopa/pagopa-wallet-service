package it.pagopa.wallet.domain.wallets

import it.pagopa.wallet.documents.wallets.Client
import java.time.Instant

data class Client(val status: Status, val lastUsage: Instant?) {
    fun toDocument(): Client = Client(status.name, lastUsage?.toString())

    enum class Status {
        ENABLED,
        DISABLED
    }

    sealed interface Id {
        val name: String

        companion object {
            fun fromString(name: String): Id = WellKnown.NAMES[name] ?: Unknown(name)
        }
    }

    enum class WellKnown : Id {
        IO;

        companion object {
            val NAMES = WellKnown.values().associateBy { it.name }
        }
    }

    data class Unknown(override val name: String) : Id
}
