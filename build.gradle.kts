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
    implementation("org.springframework.boot:spring-boot-starter-security")

    // В Boot 4 автоконфигурации вынесены в отдельные модули и не подтягиваются транзитивно.
    implementation("org.springframework.boot:spring-boot-restclient")
    implementation("org.springframework.boot:spring-boot-flyway")

    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Контракт API в виде машинно-читаемого OpenAPI: /v3/api-docs.
    // Версия 3.x — под Spring Boot 4. Именно webmvc-api, а не -ui: Swagger UI здесь
    // не нужен, а тянет за собой статику и веб-интерфейс, которого в проекте нет.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:3.1.1")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
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
    jvmArgs(
        "-XX:MaxMetaspaceSize=384m",
        "-Dfile.encoding=UTF-8",
        // Полный JIT компилирует методы под нагрузку, которой в тестах нет: профилировщик
        // работает впустую, зато Spring-контексты поднимаются быстрее.
        "-XX:TieredStopAtLevel=1",
    )
    // Форков больше одного нельзя: на машине 8 ГБ, из них свободно меньше гигабайта,
    // а каждый форк поднимает свой Spring-контекст и Testcontainers.
    maxParallelForks = 1

    // Свойства Gradle из командной строки не доходят до форка тестов сами по себе:
    // форку передаются только явно перечисленные systemProperty. Через них проходит
    // пересборка контракта OpenApiContractIT.
    //
    // Передаётся именно -P, а не -D: Gradle разбирает -Danalizer.updateOpenApi как
    // имя задачи и падает с «Task not found». Свойство Gradle такого подвоха не имеет.
    listOf("analizer.updateOpenApi").forEach { name ->
        providers.gradleProperty(name).orNull?.let { systemProperty(name, it) }
    }

    // `-Pfast` гоняет только модульные тесты, без поднятия PostgreSQL и контекста.
    // Полный прогон занимает около десяти минут, и почти всё это время уходит на старт
    // контекстов: пока идёт правка кода, запускать его не нужно.
    if (project.hasProperty("fast")) {
        filter {
            excludeTestsMatching("*IT")
            isFailOnNoMatchingTests = false
        }
    }

    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
