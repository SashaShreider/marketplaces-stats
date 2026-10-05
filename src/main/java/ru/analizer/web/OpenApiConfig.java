package ru.analizer.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Описание API для машинного чтения: {@code GET /v3/api-docs}.
 *
 * <p>Нужен фронтенду, чтобы сгенерировать типы и вызовы:
 * {@code npx openapi-typescript docs/openapi.json -o frontend/src/api/schema.d.ts}.
 * Ручное соответствие запросов и типов расходится с кодом в первую же неделю, а
 * расхождение обнаруживается уже во время сборки фронтенда.
 *
 * <p>Схема безопасности — кука сессии, а не bearer-токен. Это важно: генератор типов
 * по схеме {@code bearer} заставит клиент слать заголовок {@code Authorization},
 * которого у приложения нет, и вход перестанет работать.
 *
 * <p>Swagger UI сюда намеренно не подключён: он тянет статику и веб-интерфейс, а
 * читать контракт удобнее в редакторе кода.
 */
@Configuration
public class OpenApiConfig {

    /** Имя схемы безопасности: как она называется в сгенерированном клиенте. */
    public static final String SESSION_COOKIE = "sessionCookie";

    @Bean
    public OpenAPI analizerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("OZON Analizer API")
                        .version("1.0")
                        .description("""
                                Анализ начислений и каталога товаров OZON.

                                ## Как устроен доступ
                                Авторизация по сессии в куке, а не по токену:
                                1. `GET /api/auth/csrf` — получить куку XSRF-TOKEN.
                                2. `POST /api/auth/register` или `POST /api/auth/login`.
                                3. Дальше каждый запрос сам приносит куку JSESSIONID.

                                ## CSRF
                                Кука XSRF-TOKEN обязана быть продублирована в заголовке
                                `X-XSRF-TOKEN` для всех методов, меняющих состояние
                                (POST, PUT, DELETE). Публичный метод тоже защищён.

                                ## Разделение данных
                                Запросы возвращают только данные текущего пользователя.
                                Реквизиты OZON задаются через
                                `PUT /api/marketplaces/{mp}/credentials`.
                                """))
                .components(new Components().addSecuritySchemes(SESSION_COOKIE,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")
                                .description("Кука сессии, выданная при входе")))
                // Метод по умолчанию защищён: безопаснее ошибиться в сторону «считать
                // закрытым», чем в сторону «считать открытым».
                .addSecurityItem(new SecurityRequirement().addList(SESSION_COOKIE));
    }
}
