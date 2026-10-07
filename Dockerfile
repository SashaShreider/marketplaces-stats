# Сборка и запуск в одном образе: фронтенд, backend и статика в одном jar.
#
# Отдельные стадии сборки нужны не для красоты. Без них в итоговый образ попали бы
# исходники, node_modules, Gradle и кэш зависимостей — вместе с ними лишние сотни
# мегабайт и возможность собрать что-то с исходников контейнера.
#
# Статику мы кладём внутрь jar намеренно, а не отдаём отдельным контейнером с nginx.
# Тогда приложение отдаёт и API, и интерфейс с одного источника: кука сессии
# остаётся first-party, CORS не нужен вовсе, а разворачивать и обновлять нужно
# ровно одну вещь. Отдельный фронт имеет смысл только когда интерфейс живёт своей
# CDN-раздачей — тогда см. frontend/README.md, раздел «Отдельный статический сервер».

# ─── Фронтенд ───────────────────────────────────────────────────────────────
FROM node:22-alpine AS frontend

WORKDIR /workspace

# Слой с зависимостями переиспользуется, пока меняется только код: иначе любая
# правка .tsx заставляла бы npm качать пакеты заново.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY frontend ./

# Собирается один самодостаточный index.html: vite-plugin-singlefile встраивает
# в него и JS, и CSS. Так в образ попадает ровно один статический файл.
RUN npm run build

# ─── Backend ────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

# Кэш зависимостей живёт в отдельном слое. Копируем сначала только описание
# сборки: иначе любая правка .java пересобирала бы зависимости заново.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon --quiet

COPY src src

# Готовый интерфейс кладём в classpath до сборки jar. Spring Boot отдаёт
# содержимое static/ автоматически, отдельного контроллера для этого не нужно.
COPY --from=frontend /workspace/dist src/main/resources/static

# Полный прогон тестов здесь намеренно не запускается: он поднимает Testcontainers
# с собственным PostgreSQL, а внутри сборки обращаться к Docker нельзя. Тесты
# запускаются до сборки образа командой ./gradlew test.
RUN ./gradlew bootJar --no-daemon -x test

# ─── Запуск ─────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre

# Пользователь без прав root. У процесса, который ходит в базу и наружу, нет
# причины быть root: если что-то эксплуатируют через приложение, сломанной
# останется учётная запись без прав, а не контейнер.
RUN groupadd --system --gid 1001 analizer \
 && useradd --system --uid 1001 --gid analizer --home-dir /app --shell /usr/sbin/nologin analizer

WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar

# Только для чтения: файлы приложения никто не правит, а права на каталог
# не нужны для работы.
RUN chown -R analizer:analizer /app
USER analizer

EXPOSE 8080

# Проверка живости для Docker и compose. Внутри контейнера нужен именно локальный
# адрес: обращение к localhost извне не имеет отношения к процессам в контейнере.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD ["/bin/bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /actuator/health HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n' >&3 && grep -q '\"status\":\"UP\"' <&3"]

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -Dfile.encoding=UTF-8"

# bash, а не sh: в образе /bin/sh — это dash, который не умеет /dev/tcp. Проверка
# живости на нём молча падала бы, и контейнер помечался бы как нездоровый.
ENTRYPOINT ["/bin/bash", "-c", "exec java $JAVA_OPTS -jar app.jar"]