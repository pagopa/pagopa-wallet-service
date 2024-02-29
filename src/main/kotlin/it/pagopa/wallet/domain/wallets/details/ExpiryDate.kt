package it.pagopa.wallet.domain.wallets.details

import java.time.format.DateTimeFormatter

data class ExpiryDate(val expDate: String) {
    init {
        require(runCatching { (DateTimeFormatter.ofPattern("YYYYMM").parse(expDate)) }.isSuccess)
    }
}
