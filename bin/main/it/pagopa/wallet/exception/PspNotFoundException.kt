package it.pagopa.wallet.exception

data class PspNotFoundException(val pspId: String) : Exception("Psp with id ${pspId} not found")
