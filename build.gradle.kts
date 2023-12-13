import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

group = "it.pagopa.wallet"

version = "0.2.0"

description = "pagopa-wallet-service"

plugins {
  id("java")
  id("org.springframework.boot") version "3.0.5"
  id("io.spring.dependency-management") version "1.1.0"
  id("com.diffplug.spotless") version "6.18.0"
  id("org.openapi.generator") version "6.3.0"
  id("org.sonarqube") version "4.0.0.2929"
  id("com.dipien.semantic-version") version "2.0.0" apply false
  kotlin("plugin.spring") version "1.8.10"
  kotlin("jvm") version "1.8.10"
  jacoco
  application
}

java.sourceCompatibility = JavaVersion.VERSION_17

tasks.withType<KotlinCompile> { kotlinOptions.jvmTarget = "17" }

repositories { mavenCentral() }

val ecsLoggingVersion = "1.5.0"

dependencyManagement {
  imports { mavenBom("org.springframework.boot:spring-boot-dependencies:3.0.5") }
  imports { mavenBom("com.azure.spring:spring-cloud-azure-dependencies:4.0.0") }
  // Kotlin BOM
  imports { mavenBom("org.jetbrains.kotlin:kotlin-bom:1.7.22") }
  imports { mavenBom("org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.6.4") }
}

dependencies {
  implementation("io.projectreactor:reactor-core")
  implementation("io.projectreactor.netty:reactor-netty")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-validation")
  implementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")
  implementation("org.springframework.boot:spring-boot-starter-data-redis")
  implementation("com.azure.spring:spring-cloud-azure-starter")
  implementation("com.azure.spring:spring-cloud-azure-starter-data-cosmos")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-web-services")
  implementation("org.glassfish.jaxb:jaxb-runtime")
  implementation("jakarta.xml.bind:jakarta.xml.bind-api")
  implementation("io.swagger.core.v3:swagger-annotations:2.2.8")
  implementation("org.apache.httpcomponents:httpclient")
  implementation("com.google.code.findbugs:jsr305:3.0.2")
  implementation("org.projectlombok:lombok")
  implementation("org.openapitools:openapi-generator-gradle-plugin:6.5.0")
  implementation("org.openapitools:jackson-databind-nullable:0.2.6")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
  implementation("org.springframework.boot:spring-boot-starter-aop")
  implementation("io.netty:netty-resolver-dns-native-macos:4.1.90.Final")
  implementation("com.diffplug.spotless:spotless-plugin-gradle:6.18.0")
  // Kotlin dependencies
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

  // ECS logback encoder
  implementation("co.elastic.logging:logback-ecs-encoder:$ecsLoggingVersion")

  runtimeOnly("org.springframework.boot:spring-boot-devtools")
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.mockito:mockito-inline")
  testImplementation("io.projectreactor:reactor-test")
  // Kotlin dependencies
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
  testImplementation("org.mockito.kotlin:mockito-kotlin:4.0.0")
}

configurations {
  implementation.configure {
    exclude(module = "spring-boot-starter-web")
    exclude("org.apache.tomcat")
    exclude(group = "org.slf4j", module = "slf4j-simple")
  }
}
// Dependency locking - lock all dependencies
dependencyLocking { lockAllConfigurations() }

sourceSets {
  main {
    java { srcDirs("$buildDir/generated/src/main/java") }
    kotlin { srcDirs("src/main/kotlin", "$buildDir/generated/src/main/kotlin") }
    resources { srcDirs("src/resources") }
  }
}

springBoot {
  mainClass.set("it.pagopa.wallet.WalletApplicationKt")
  buildInfo { properties { additional.set(mapOf("description" to project.description)) } }
}

tasks.create("applySemanticVersionPlugin") {
  dependsOn("prepareKotlinBuildScriptModel")
  apply(plugin = "com.dipien.semantic-version")
}

/*
Used java generator for wallet classes because of the following issue with kotlin generator
https://github.com/OpenAPITools/openapi-generator/issues/14949
*/
tasks.register("wallet", GenerateTask::class.java) {
  generatorName.set("spring")
  inputSpec.set("$rootDir/api-spec/wallet-api.yaml")
  outputDir.set("$buildDir/generated")
  apiPackage.set("it.pagopa.generated.wallet.api")
  modelPackage.set("it.pagopa.generated.wallet.model")
  generateApiTests.set(false)
  generateApiDocumentation.set(false)
  generateApiTests.set(false)
  generateModelTests.set(false)
  library.set("spring-boot")
  modelNameSuffix.set("Dto")
  configOptions.set(
    mapOf(
      "swaggerAnnotations" to "false",
      "openApiNullable" to "true",
      "interfaceOnly" to "true",
      "hideGenerationTimestamp" to "true",
      "skipDefaultInterface" to "true",
      "useSwaggerUI" to "false",
      "reactive" to "true",
      "useSpringBoot3" to "true",
      "oas3" to "true",
      "generateSupportingFiles" to "true",
      "enumPropertyNaming" to "UPPERCASE"
    )
  )
}

tasks.register("nexiNpg", GenerateTask::class.java) {
  generatorName.set("java")
  inputSpec.set("$rootDir/npg-api/npg-api.yaml")
  outputDir.set("$buildDir/generated")
  apiPackage.set("it.pagopa.generated.npg.api")
  modelPackage.set("it.pagopa.generated.npg.model")
  generateApiTests.set(false)
  generateApiDocumentation.set(false)
  generateApiTests.set(false)
  generateModelTests.set(false)
  library.set("webclient")
  configOptions.set(
    mapOf(
      "swaggerAnnotations" to "false",
      "openApiNullable" to "true",
      "interfaceOnly" to "true",
      "hideGenerationTimestamp" to "true",
      "skipDefaultInterface" to "true",
      "useSwaggerUI" to "false",
      "reactive" to "true",
      "useSpringBoot3" to "true",
      "oas3" to "true",
      "generateSupportingFiles" to "false"
    )
  )
}

tasks.register("ecommercePaymentMethod", GenerateTask::class.java) {
  generatorName.set("java")
  remoteInputSpec.set(
    "https://raw.githubusercontent.com/pagopa/pagopa-infra/main/src/domains/ecommerce-app/api/ecommerce-payment-methods-service/v1/_openapi.json.tpl"
  )
  outputDir.set("$buildDir/generated")
  apiPackage.set("it.pagopa.generated.ecommerce.api")
  modelPackage.set("it.pagopa.generated.ecommerce.model")
  generateApiTests.set(false)
  generateApiDocumentation.set(false)
  generateApiTests.set(false)
  generateModelTests.set(false)
  library.set("webclient")
  configOptions.set(
    mapOf(
      "swaggerAnnotations" to "false",
      "openApiNullable" to "true",
      "interfaceOnly" to "true",
      "hideGenerationTimestamp" to "true",
      "skipDefaultInterface" to "true",
      "useSwaggerUI" to "false",
      "reactive" to "true",
      "useSpringBoot3" to "true",
      "oas3" to "true",
      "generateSupportingFiles" to "false"
    )
  )
}

tasks.register("nexiNpgNotification", GenerateTask::class.java) {
  generatorName.set("kotlin-spring")
  inputSpec.set("$rootDir/npg-api/npg-notification-api.yaml")
  outputDir.set("$buildDir/generated")
  apiPackage.set("it.pagopa.generated.npgnotification.api")
  modelPackage.set("it.pagopa.generated.npgnotification.model")
  generateApiTests.set(false)
  generateApiDocumentation.set(false)
  generateApiTests.set(false)
  generateModelTests.set(false)
  library.set("spring-boot")
  modelNameSuffix.set("Dto")
  configOptions.set(
    mapOf(
      "swaggerAnnotations" to "false",
      "openApiNullable" to "true",
      "interfaceOnly" to "true",
      "hideGenerationTimestamp" to "true",
      "skipDefaultInterface" to "true",
      "useSwaggerUI" to "false",
      "reactive" to "true",
      "useSpringBoot3" to "true",
      "oas3" to "true",
      "generateSupportingFiles" to "true",
      "enumPropertyNaming" to "UPPERCASE"
    )
  )
}

tasks.withType<KotlinCompile> {
  dependsOn("wallet", "nexiNpg", "nexiNpgNotification", "ecommercePaymentMethod")
  kotlinOptions.jvmTarget = "17"
}

tasks.withType(JavaCompile::class.java).configureEach { options.encoding = "UTF-8" }

tasks.withType(Javadoc::class.java).configureEach { options.encoding = "UTF-8" }

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
  kotlin {
    toggleOffOn()
    targetExclude("build/**/*")
    ktfmt().kotlinlangStyle()
  }
  kotlinGradle {
    toggleOffOn()
    targetExclude("build/**/*.kts")
    ktfmt().googleStyle()
  }
  java {
    target("**/*.java")
    targetExclude("build/**/*")
    eclipse().configFile("eclipse-style.xml")
    toggleOffOn()
    removeUnusedImports()
    trimTrailingWhitespace()
    endWithNewline()
  }
}

tasks.named<Jar>("jar") { enabled = false }

tasks.test {
  useJUnitPlatform()
  finalizedBy(tasks.jacocoTestReport) // report is always generated after tests run
}

tasks.jacocoTestReport {
  dependsOn(tasks.test) // tests are required to run before generating the report

  classDirectories.setFrom(
    files(
      classDirectories.files.map {
        fileTree(it).matching { exclude("it/pagopa/wallet/WalletApplicationKt.class") }
      }
    )
  )

  reports { xml.required.set(true) }
}

/**
 * Task used to expand application properties with build specific properties such as artifact name
 * and version
 */
tasks.processResources { filesMatching("application.properties") { expand(project.properties) } }
