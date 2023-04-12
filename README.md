# Template for Java Spring Microservice project

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=TODO-set-your-id&metric=alert_status)](https://sonarcloud.io/dashboard?id=TODO-set-your-id)

TODO: add a description

TODO: generate a index with this tool: https://ecotrust-canada.github.io/markdown-toc/

TODO: resolve all the TODOs in this template

---

## Api Documentation 📖

See the [OpenApi 3 here.](TODO: set your url)

---

## Technology Stack

- Java 17
- Spring Boot

---

## Start Project Locally 🚀

### Prerequisites

- docker

### Run docker container

from `./docker` directory

`sh ./run_docker.sh dev`

ℹ️ Note: for PagoPa ACR is required the login `az acr login -n <acr-name>`

---

## Develop Locally 💻

### Prerequisites

- git
- gradle
- jdk-17

### Run the project

TODO

### Testing 🧪

#### Unit testing

To run the **Junit** tests:

TODO

#### Integration testing

TODO

#### Performance testing

install [k6](https://k6.io/) and then from `./performance-test/src`

1. `k6 run --env VARS=local.environment.json --env TEST_TYPE=./test-types/load.json main_scenario.js`

---

## Contributors 👥

Made with ❤️ by PagoPa S.p.A.

### Mainteiners

See `CODEOWNERS` file
