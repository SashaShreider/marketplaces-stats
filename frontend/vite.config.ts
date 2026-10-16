import path from "path";
import { fileURLToPath } from "url";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";
import { viteSingleFile } from "vite-plugin-singlefile";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Порт бэкенда на этой машине. Тот же, что у docker-compose для APP_PORT, поэтому
// адрес не приходится дублировать: поменяли порт в .env — он здесь подхватится.
const apiPort = process.env.APP_PORT ?? "8080";

// https://vite.dev/config/
export default defineConfig({
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
});
