import org.gradle.api.tasks.JavaExec

plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.0.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.ai.agenticrag"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.spring.io/milestone")
}

ext {
    set("springAiVersion", "2.0.0-M1")
    set("testcontainersVersion", "1.20.4")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
        mavenBom("org.testcontainers:testcontainers-bom:${property("testcontainersVersion")}")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql:10.17.0")
    implementation("org.flywaydb:flyway-core:10.17.0")
    runtimeOnly("org.postgresql:postgresql")

    implementation("com.pgvector:pgvector:0.1.6")

    implementation("org.springframework.ai:spring-ai-starter-model-ollama")

    implementation("org.apache.tika:tika-core:2.9.2")
    implementation("org.apache.tika:tika-parsers-standard-package:2.9.2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

tasks.test {
    useJUnitPlatform()
    // JaCoCo 0.8.12 doesn't support Java 25 yet (class file version 69)
    // Re-enable when JaCoCo adds Java 25 support
    // finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/AgenticRagApplication.class",
                    "**/IngestFolderCli.class"
                )
            }
        })
    )
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()
            }
        }
    }
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/AgenticRagApplication.class",
                    "**/IngestFolderCli.class"
                )
            }
        })
    )
}

tasks.register<JavaExec>("ingestFolder") {
    group = "app"
    description = "Ingest a folder of PDFs/TXT/MD into pgvector (uses Apache Tika). Pass folder via --args"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.ai.agenticrag.ingest.IngestFolderCli")
}
