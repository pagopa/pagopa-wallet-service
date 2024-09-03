package it.pagopa.wallet.audit

data class AuditWalletApplication(
    val id: String,
    val status: String,
    val creationDate: String,
    val updateDate: String,
    val metadata: Map<String, String?>
) {}
