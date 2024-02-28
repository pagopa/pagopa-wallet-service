package it.pagopa.wallet.domain.applications

import java.time.Instant

data class Application(
    val id: ApplicationId,
    val description: ApplicationDescription,
    val status: ApplicationStatus,
    val creationDate: Instant,
    val updateDate: Instant
)
