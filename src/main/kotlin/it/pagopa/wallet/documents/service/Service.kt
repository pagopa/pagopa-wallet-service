package it.pagopa.wallet.documents.service

data class Service(val id: String, val name: String, val status: String, val lastUpdated: String) {
    companion object {
        fun fromDomain(service: it.pagopa.wallet.domain.services.Service): Service =
            Service(
                service.id.id.toString(),
                service.name.name,
                service.status.name,
                service.lastUpdated.toString()
            )
    }
}
