import path from "path";
import { fileURLToPath } from "url";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig, loadEnv } from "vite";
import { viteSingleFile } from "vite-plugin-singlefile";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

export default defineConfig(({ mode }) => {
  // .env лежит в корне проекта, а конфиг — во frontend/. Vite по умолчанию берёт
  // переменные из своей папки, поэтому APP_PORT оттуда не доходил и прокси всегда
  // смотрел на 8080. Пустой префикс — читаем все переменные, а не только VITE_*:
  // этот настройка серверная, клиентской она не должна быть.
  const env = loadEnv(mode, path.resolve(__dirname, ".."), "");
  const apiPort = env.APP_PORT || "8080";

  return {
    plugins: [react(), tailwindcss(), viteSingleFile()],
    resolve: {
      alias: {
        "@": path.resolve(__dirname, "src"),
      },
    },
    server: {
      // Явный IPv4 вместо `localhost`. По умолчанию Node резолвит localhost в IPv6
      // первым, и на этой машине Vite поднимался только на [::1]:5173 — браузер,
      // обращающийся к 127.0.0.1, получал отказ, и страница не открывалась вовсе.
      host: "127.0.0.1",
      port: 5173,
      // Занятый порт должен быть ошибкой, а не молчаливым переездом на другой:
      // иначе страница откроется не на том адресе, который указан в инструкции.
      strictPort: true,
      // Запросы к API идут через сам Vite, а не напрямую на бэкенд. Так адрес
      // порта не приходится знать интерфейсу, и — важнее — браузер видит один
      // источник, поэтому CORS на 5173 не нужен вовсе. На сервере прокси не
      // работает: там Vite не запускается, статику отдаёт приложение.
      proxy: {
        "/api": {
          target: `http://127.0.0.1:${apiPort}`,
          changeOrigin: false,
        },
      },
    },
  };
});
