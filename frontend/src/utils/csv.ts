/**
 * Выгрузка таблицы в CSV.
 *
 * <p>Свой маленький модуль, а не библиотека: CSV — это четыре правила (разделитель,
 * кавычки, перевод строки, BOM), и любая зависимость ради них приносит с собой
 * настройки, которые потом всё равно приходится править.
 *
 * <h2>Почему точка с запятой и BOM</h2>
 * Файл открывают в Excel на русской машине, а он в этой локали ждёт `;` как
 * разделитель списка. С запятой весь файл лёг бы в одну колонку, и это выглядело бы
 * как испорченная выгрузка. BOM (U+FEFF) в начале нужен, чтобы Excel не принял UTF-8
 * за однобайтовую кодировку и не превратил кириллицу в «Ð¡ÑƒÐ¼Ð¼Ð°».
 *
 * <h2>Почему числа без форматирования</h2>
 * В выгрузку идут сырые числа с точкой как десятичным разделителем: `-12879.33`, а
 * не `−12 879,33 ₽`. Отформатированная строка в таблице читается, но в Excel это
 * текст, который нельзя сложить. Знак минус — обычный дефис, а не типографский:
 * U+2212 Excel числом не считает.
 */

/** Символ, который Excel понимает как «файл в UTF-8». */
const BOM = '\uFEFF'

/**
 * Экранирует одно поле.
 *
 * <p>Кавычки удваиваются, а не экранируются обратной косой чертой: удвоение —
 * единственный способ, который понимает CSV. Поле в кавычках нужно, если в нём есть
 * разделитель, кавычка или перевод строки — иначе строка развалится на лишние
 * колонки, и заметить это можно будет только в Excel.
 */
export function csvField(value: string | number | null | undefined): string {
  if (value === null || value === undefined) return ''
  const s = String(value)
  if (/[";\r\n]/.test(s)) return `"${s.replace(/"/g, '""')}"`
  return s
}

/** Собирает CSV из готовых строк. */
export function toCsv(rows: (string | number | null | undefined)[][]): string {
  return BOM + rows.map((r) => r.map(csvField).join(';')).join('\r\n') + '\r\n'
}

/**
 * Отдаёт файл браузеру.
 *
 * <p>Ссылка создаётся и удаляется вручную: `URL.createObjectURL` держит ссылку на
 * данные, пока её не отозвать, и на большой выгрузке это утечка до перезагрузки
 * страницы.
 *
 * @param filename имя файла; расширение добавляется здесь, чтобы вызывающий не
 *                 повторял его в каждом месте
 */
export function downloadCsv(filename: string, content: string): void {
  const name = /\.csv$/i.test(filename) ? filename : `${filename}.csv`
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8;' })
  const href = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = href
  link.download = name
  link.style.display = 'none'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  // Отзываем в следующем тике: в некоторых браузерах немедленный отзыв обрывает
  // начавшуюся загрузку.
  window.setTimeout(() => URL.revokeObjectURL(href), 0)
}

/**
 * Безопасное имя файла: без символов, которые запрещены в путях Windows.
 *
 * <p>Название магазина приходит от маркетплейса и может содержать что угодно,
 * включая `/` и `:`; файл с таким именем Windows не сохранит.
 */
export function safeFilename(parts: string[]): string {
  return parts
    .filter((p) => p)
    .join('_')
    .replace(/[\\/:*?"<>|\s]+/g, '_')
    .replace(/_+/g, '_')
    .replace(/^_|_$/g, '')
    .slice(0, 120)
}
