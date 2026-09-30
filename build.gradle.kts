plugins {
    java
    id("org.springframework.boot") version "4.1.1"
}

group = "ru.analizer"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Spring Boot 4 управляет версиями через Gradle-платформу, а не через отдельный плагин
    // io.spring.dependency-management: тот конфликтует с platform()-зависимостями
    // (например, с BOM Testcontainers ниже).
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // В Boot 4 автоконфигурации вынесены в отдельные модули и не подтягиваются транзитивно.
    implementation("org.springframework.boot:spring-boot-restclient")
    implementation("org.springframework.boot:spring-boot-flyway")

    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    // Boot 4.1 управляет Testcontainers 2.0.5. В Testcontainers 2.x модули
    // называются testcontainers-<tech>, а не <tech>.
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:deprecation", "-Xlint:unchecked"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Spring Boot-контекст, Hibernate и Testcontainers в одном процессе: без явных
    // границ JVM падает с "insufficient memory for the Java Runtime Environment".
    maxHeapSize = "768m"
    jvmArgs("-XX:MaxMetaspaceSize=384m", "-Dfile.encoding=UTF-8")
    maxParallelForks = 1
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
