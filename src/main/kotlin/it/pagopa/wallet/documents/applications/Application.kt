package it.pagopa.wallet.documents.applications

import org.springframework.data.mongodb.core.mapping.Document

@Document("applications")
data class Application(
    val id: String,
    val description: String,
    val status: String,
    val creationDate: String,
    val lastUpdated: String
) {
    companion object {
        fun fromDomain(application: it.pagopa.wallet.domain.applications.Application): Application =
            Application(
                application.id.id.toString(),
                application.description.toString(),
                application.status.name,
                application.creationDate.toString(),
                application.lastUpdated.toString()
            )
    }
}
