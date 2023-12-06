package it.pagopa.wallet.domain.services

enum class ServiceStatus {
    ENABLED,
    INCOMING,
    DISABLED;

    companion object {
        fun canChangeToStatus(requested: ServiceStatus, global: ServiceStatus): Boolean {
            /*
                Truth table (E=ENABLED, D=DISABLED, I=INCOMING)

                       Requested
                        E  D  I
                 G     ┌──┬──┬──┐
                 l   E │OK│OK│NO│
                 o     ├──┼──┼──┤
                 b   D │NO│OK│NO│
                 a     ├──┼──┼──┤
                 l   I │NO│OK│OK│
                       └──┴──┴──┘
            */

            return when (requested) {
                ENABLED -> global == ENABLED
                INCOMING -> global == INCOMING
                DISABLED -> true
            }
        }
    }
}
