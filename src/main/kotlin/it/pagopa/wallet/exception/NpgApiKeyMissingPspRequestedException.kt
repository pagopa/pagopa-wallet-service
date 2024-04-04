package it.pagopa.wallet.exception

/** Exception thrown when requesting an API key from NPG configuration for a nonexisting PSP */
class NpgApiKeyMissingPspRequestedException(psp: String, availablePsps: Set<String>) :
    NpgApiKeyConfigurationException(
        "Requested API key for PSP: [$psp]. Available PSPs: $availablePsps"
    )
