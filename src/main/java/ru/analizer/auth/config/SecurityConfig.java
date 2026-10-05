package ru.analizer.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Авторизация по сессии в куке.
 *
 * <p>Почему сессия, а не JWT: сессию можно оборвать. «Выйти на всех устройствах» в JWT
 * превращается в самописный чёрный список, который потом забывают чистить.
 *
 * <p>CSRF включён, потому что кука приезжает в браузер автоматически. Фронтенд читает
 * куку XSRF-TOKEN и повторяет её значение в заголовке. Надеяться на один SameSite как
 * на единственную защиту нельзя: у него отключённых сценариев больше, чем кажется.
 */
@Configuration
public class SecurityConfig {

    /** Методы, доступные без входа. Всё остальное требует сессии. */
    private static final String[] PUBLIC_PATHS = {
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/csrf",
            "/actuator/health",
            // Контракт нужен фронтенду до входа — по нему генерируются типы.
            "/v3/api-docs",
            "/v3/api-docs/**"
    };

    /** BCrypt со стандартной стоимостью: смена соли инвалидирует хэши всех пользователей. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Проверка пароля при входе.
     *
     * <p>Собирается явно, а не берётся из автоконфигурации: {@code formLogin} выключен,
     * поэтому автоматического фильтра входа нет. Без менеджера проверку пароля пришлось бы
     * написать вручную — рядом с той, что делает Spring. Две реализации аутентификации
     * со временем разошлись бы: одна начала бы учитывать что-то новое, другая нет.
     */
    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    /**
     * Где живёт аутентификация между запросами.
     *
     * <p>Бин нужен контроллеру входа: он сохраняет контекст сам, потому что фильтра формы
     * входа нет — он выключен.
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * Разрешённые источники.
     *
     * <p>Пока список пуст, поведение прежнее: браузер ходит только с нашего источника.
     * Как только в {@code app.cors.allowed-origins} появляется адрес сервера разработки,
     * запросы с него начинают проходить вместе с куками.
     *
     * <p>Отдельный бин нужен ещё и потому, что {@code http.cors(...)} ставит
     * {@code CorsFilter} <b>до</b> фильтра CSRF. Иначе предварительный запрос
     * {@code OPTIONS} от чужого источника отклонился бы как подделка CSRF, и браузер
     * не отправил бы основной запрос.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        if (properties.hasAllowedOrigins()) {
            configuration.setAllowedOrigins(properties.allowedOrigins());
            // Явно, а не «звёздочка с куками»: браузер всё равно не примет «*» вместе
            // с credentials, и отказ выглядел бы как загадочная ошибка сети.
            configuration.setAllowCredentials(true);
            configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
            // Своё значение куки XSRF-TOKEN видно JavaScript, и клиент дублирует его
            // в заголовке с таким же именем.
            configuration.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
            configuration.setExposedHeaders(List.of("Location"));
        }

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (properties.hasAllowedOrigins()) {
            source.registerCorsConfiguration("/api/**", configuration);
        }
        return source;
    }

    /**
     * Кука с CSRF-токеном.
     *
     * <p>Отдельный бин, а не {@code withHttpOnlyFalse()} прямо в фильтре, потому что
     * здесь задаются ещё SameSite и Secure. Кука CSRF обязана быть видна JavaScript,
     * иначе SPA не прочитает токен и не приложит его к заголовку — а это выглядит
     * не как «сломан CORS», а как «все POST-методы отклонены».
     */
    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository(CookieProperties properties) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie
                .sameSite(properties.sameSiteAttribute())
                .secure(properties.secure()));
        return repository;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, CookieCsrfTokenRepository csrfTokenRepository)
            throws Exception {
        // Токен из куки должен совпадать со значением в заголовке. По умолчанию Spring
        // маскирует токен при разборе заголовка (защита от BREACH), а кука хранит
        // «сырое» значение — и проверка всегда отклоняла бы запрос. Отключаем маскирование.
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);

        return http
                // Кука сама приносит сессию; basic и form login перебили бы её
                // собственными диалогами браузера.
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                // CORS настраивается здесь, а не в MVC: фильтр должен отработать раньше
                // проверки CSRF, иначе предварительный OPTIONS будет отклонён.
                .cors(cors -> { })
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                // 401 вместо редиректа на страницу входа: бэкенд не отдаёт страниц,
                // а решает, какую показать, фронтенд.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .sessionManagement(session -> session
                        .sessionFixation(fixation -> fixation.migrateSession()))
                .csrf(csrf -> csrf
                        // Токен доступен JavaScript: иначе SPA не приложит его
                        // к заголовку и все POST-методы будут отклонены.
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfHandler))
                .build();
    }
}