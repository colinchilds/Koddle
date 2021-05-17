plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
    jcenter()
}

group = "me.koddle"
version = "1.0-SNAPSHOT"

val vertxVersion = "4.0.3"
val kotlinVersion = "1.5.0"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")

    implementation("io.vertx:vertx-core:$vertxVersion")
    implementation("io.vertx:vertx-web:$vertxVersion")
    implementation("io.vertx:vertx-web-api-contract:$vertxVersion")
    implementation("io.vertx:vertx-lang-kotlin-coroutines:$vertxVersion")
    implementation("io.vertx:vertx-unit:$vertxVersion")
    implementation("io.vertx:vertx-lang-kotlin:$vertxVersion")
    implementation("io.vertx:vertx-pg-client:$vertxVersion")
    implementation("io.vertx:vertx-config:$vertxVersion")
    implementation("io.vertx:vertx-auth-jwt:$vertxVersion")
    implementation("org.flywaydb:flyway-core:6.0.0")
    implementation("postgresql:postgresql:9.1-901-1.jdbc4")
    implementation("org.koin:koin-core:2.0.1")
    implementation("org.koin:koin-core-ext:2.0.1")
    implementation("com.google.guava:guava:28.1-jre")

    implementation("org.reflections:reflections:0.9.11")
    implementation("org.slf4j:slf4j-jdk14:1.7.28")
    implementation("io.vertx:vertx-web-client:$vertxVersion")

    implementation("org.apache.commons:commons-collections4:4.0")
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "11"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "11"
    }
}