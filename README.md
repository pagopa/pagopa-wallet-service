# PagoPA Wallet Service

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=pagopa_pagopa-wallet-service&metric=alert_status)](https://sonarcloud.io/dashboard?id=pagopa_pagopa-wallet-service)

This microservice is responsible for keeping wallets inside the PagoPA platform.
Wallets are collections of payment instruments with a wallet identifier, and may be used both for guest and for authenticated payments.

For authenticated payments, wallets are used to remember registered payment instruments for subsequent payments, while for
guest (i.e. unauthenticated) payments, wallet are ephemeral and contain only the payment instrument used for the payment session.

- [PagoPA Wallet Service](#pagopa-wallet-service)
    * [Api Documentation 📖](#api-documentation-)
    * [Technology Stack](#technology-stack)
    * [Start Project Locally 🚀](#start-project-locally-)
        + [Prerequisites](#prerequisites)
        + [Run docker container](#run-docker-container)
    * [Develop Locally 💻](#develop-locally-)
        + [Prerequisites](#prerequisites-1)
        + [Run the project](#run-the-project)
        + [Testing 🧪](#testing-)
            - [Unit testing](#unit-testing)
            - [Integration testing](#integration-testing)
            - [Performance testing](#performance-testing)
    * [Contributors 👥](#contributors-)
        + [Maintainers](#maintainers)

<small><i><a href='http://ecotrust-canada.github.io/markdown-toc/'>Table of contents generated with markdown-toc</a></i></small>

---

## Api Documentation 📖

See the [OpenAPI 3 here.](https://editor.swagger.io/?url=https://raw.githubusercontent.com/pagopa/pagopa-wallet-service/main/api-spec/wallet-api.yaml)

---

## Technology Stack

- Kotlin
- Spring Boot

---

## Start Project Locally 🚀

### Prerequisites

- docker

### Populate the environment

The microservice needs a valid `.env` file in order to be run.

If you want to start the application without too much hassle, you can just copy `.env.example` with

```shell
$ cp .env.example .env
```

to get a good default configuration.

If you want to customize the application environment, reference this table:


| Variable name                  | Description                                                       | type              | default |
|--------------------------------|-------------------------------------------------------------------|-------------------|---------|
| NPG_SERVICE_URI                | URL used to contact the payment gateway                           | string            |         |
| NPG_SERVICE_READ_TIMEOUT       | Timeout for requests towards the payment gateway                  | string            |         |
| NPG_SERVICE_CONNECTION_TIMEOUT | Timeout for estabilishing connections towards the payment gateway | string            |         |
| NPG_SERVICE_API_KEY            | Payment gateway API key                                           | string            |         |
| MONGO_HOST                     | Host where MongoDB instance used to persist wallet data           | hostname (string) |         |
| MONGO_PORT                     | Port where MongoDB is bound to in MongoDB host                    | number            |         |
| MONGO_USERNAME                 | MongoDB username used to connect to the database                  | string            |         |
| MONGO_PASSWORD                 | MongoDB password used to connect to the database                  | string            |         |
| MONGO_SSL_ENABLED              | Whether SSL is enabled while connecting to MongoDB                | string            |         |

### Run docker container

```shell
$ docker compose up --build
```

---

## Develop Locally 💻

### Prerequisites

- git
- gradle
- jdk-17

### Run the project

```shell
$ ./gradlew bootRun
```

### Testing 🧪

#### Unit testing

To run the **Junit** tests:

```shell
$ ./gradlew test
```

#### Integration testing

TODO

#### Performance testing

install [k6](https://k6.io/) and then from `./performance-test/src`

1. `k6 run --env VARS=local.environment.json --env TEST_TYPE=./test-types/load.json main_scenario.js`

---

## Contributors 👥

Made with ❤️ by PagoPA S.p.A.

### Maintainers

See `CODEOWNERS` file
