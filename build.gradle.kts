plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
    jcenter()
}

group = "me.koddle"
version = "1.0-SNAPSHOT"

val vertxVersion = "4.1.2"
val kotlinVersion = "1.5.0"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")

    implementation("io.vertx:vertx-core:$vertxVersion")
    implementation("io.vertx:vertx-web:$vertxVersion")
    implementation("io.vertx:vertx-web-client:$vertxVersion")
    implementation("io.vertx:vertx-web-api-contract:$vertxVersion")
    implementation("io.vertx:vertx-lang-kotlin-coroutines:$vertxVersion")
    implementation("io.vertx:vertx-unit:$vertxVersion")
    implementation("io.vertx:vertx-lang-kotlin:$vertxVersion")
    implementation("io.vertx:vertx-pg-client:$vertxVersion")
    implementation("io.vertx:vertx-config:$vertxVersion")
    implementation("org.postgresql:postgresql:42.2.22")
    implementation("com.google.guava:guava:28.1-jre")

    implementation("org.reflections:reflections:0.9.11")
    implementation("org.slf4j:slf4j-jdk14:1.7.32")

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