<#
.SYNOPSIS
  Проверяет, что приложение живо и отдаёт честные данные.

.DESCRIPTION
  Скрипт не доверяет коду и проверяет то, что увидит клиент:

    1. гость получает 401, а не редирект;
    2. пользователь заводится и входит;
    3. реквизиты маркетплейса принимаются (их проверяет настоящий запрос к OZON);
    4. отчёт по дням сходится тождеством доходы − расходы = к выплате;
    5. отчёт по товарам сходится с дневным: расходы по товарам плюс нераспределённые
       дают те же расходы по дням;
    6. секретный ключ не утекает ни в один ответ.

  Скрипт ничего не чинит и не чистит: он только читает и сообщает.

.PARAMETER Base
  Адрес приложения. По умолчанию http://localhost:8080

.PARAMETER DateFrom
  Начало периода. По умолчанию 2026-09-01

.PARAMETER DateTo
  Конец периода. По умолчанию 2026-09-30

.PARAMETER Login
  Логин пользователя. По умолчанию test

.PARAMETER Password
  Пароль. По умолчанию test-test-1. Пароль короче 8 символов приложение не примет.

.PARAMETER SkipCredentials
  Не подключать маркетплейс заново. Полезно, если реквизиты уже заданы.

.PARAMETER RunImports
  Сначала запустить импорт каталога и финансов за период, дождаться окончания.
  По умолчанию выключено: скрипт проверяет то, что уже загружено.

.EXAMPLE
  .\scripts\check-api.ps1
  Проверить то, что уже загружено.

.EXAMPLE
  .\scripts\check-api.ps1 -RunImports -DateFrom 2026-08-01 -DateTo 2026-08-31
  Загрузить август и проверить.

.NOTES
  Реквизиты OZON читаются из .env: OZON_CLIENT_ID и OZON_API_KEY.
#>
[CmdletBinding()]
param(
  [string]$Base = 'http://localhost:8080',
  [string]$DateFrom = '2026-09-01',
  [string]$DateTo = '2026-09-30',
  [string]$Login = 'test',
  [string]$Password = 'test-test-1',
  [switch]$SkipCredentials,
  [switch]$RunImports
)

$ErrorActionPreference = 'Stop'
$OutputEncoding = [System.Text.Encoding]::UTF8

$script:Failures = 0
$script:Checks = 0

function Write-Step { param([string]$Text) Write-Host "`n=== $Text" -ForegroundColor Cyan }
function Write-Ok { param([string]$Text) Write-Host "  [ок]   $Text" -ForegroundColor Green }
function Write-Bad { param([string]$Text) Write-Host "  [плохо] $Text" -ForegroundColor Red; $script:Failures++ }
function Write-Note { param([string]$Text) Write-Host "  [инфо] $Text" -ForegroundColor DarkGray }

function Assert-True {
    param([bool]$Condition, [string]$Ok, [string]$Bad)
    $script:Checks++
    if ($Condition) { Write-Ok $Ok } else { Write-Bad $Bad }
}

# --- сессия с куками и CSRF -------------------------------------------------

$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession

function Get-CsrfToken {
    Invoke-RestMethod -Uri "$Base/api/auth/csrf" -WebSession $session | Out-Null
    $cookie = $session.Cookies.GetCookies($Base) | Where-Object { $_.Name -eq 'XSRF-TOKEN' }
    if (-not $cookie) { throw 'Сервер не выдал куку XSRF-TOKEN' }
    return $cookie.Value
}

function Invoke-Api {
    param([string]$Path, [string]$Method = 'GET', $Body = $null)
    $headers = @{}
    if ($Method -ne 'GET') { $headers['X-XSRF-TOKEN'] = Get-CsrfToken }
    $params = @{ Uri = "$Base$Path"; Method = $Method; WebSession = $session; Headers = $headers }
    if ($null -ne $Body) {
        $params['ContentType'] = 'application/json'
        $params['Body'] = ($Body | ConvertTo-Json -Compress -Depth 5)
    }
    Invoke-RestMethod @params
}

# Ответ с кодом: нужен там, где 4xx — ожидаемый исход проверки.
function Invoke-ApiExpectingFailure {
    param([string]$Path, [string]$Method = 'GET')
    try {
        Invoke-RestMethod -Uri "$Base$Path" -Method $Method -WebSession $session | Out-Null
        return @{ Status = 200; Body = '' }
    } catch {
        return @{ Status = [int]$_.Exception.Response.StatusCode; Body = $_.ErrorDetails.Message }
    }
}

function Read-OzonCredentials {
    $envFile = Join-Path $PSScriptRoot '..\.env'
    if (-not (Test-Path $envFile)) { return $null }
    $values = @{}
    Get-Content $envFile | ForEach-Object {
        if ($_ -notmatch '^\s*#' -and $_ -match '=') {
            $key, $value = $_ -split '=', 2
            $values[$key.Trim()] = $value.Trim()
        }
    }
    if ($values['OZON_CLIENT_ID'] -and $values['OZON_API_KEY']) {
        return @{ ClientId = $values['OZON_CLIENT_ID']; ApiKey = $values['OZON_API_KEY'] }
    }
    return $null
}

function Wait-Import {
    param([long]$Id, [int]$Minutes = 30)
    $deadline = (Get-Date).AddMinutes($Minutes)
    while ((Get-Date) -lt $deadline) {
        $progress = Invoke-Api "/api/marketplaces/ozon/imports/$Id"
        if (-not $progress.inProgress) { return $progress }
        $done = if ($null -ne $progress.doneUnits) { $progress.doneUnits } else { $progress.doneDays }
        $total = if ($null -ne $progress.totalUnits) { $progress.totalUnits } else { $progress.totalDays }
        Write-Host "    … $done / $total" -ForegroundColor DarkGray
        Start-Sleep -Seconds 5
    }
    throw "Импорт $Id не завершился за $Minutes минут"
}

function Get-Number { param($Value) if ($null -eq $Value) { return 0.0 } return [double]$Value }

# --- проверки ---------------------------------------------------------------

Write-Host 'Проверка приложения' -ForegroundColor White
Write-Host "  адрес:  $Base"
Write-Host "  период: $DateFrom … $DateTo"

Write-Step 'Приложение отвечает'
try {
    Invoke-RestMethod -Uri "$Base/actuator/health" -TimeoutSec 10 | Out-Null
    Write-Ok 'живо (/actuator/health)'
} catch {
    Write-Bad "не отвечает: $($_.Exception.Message)"
    Write-Host "`nПриложение не запущено. Поднимите его: .\gradlew.bat bootRun" -ForegroundColor Yellow
    exit 1
}

Write-Step 'Гость не проходит'
$guest = Invoke-ApiExpectingFailure '/api/marketplaces'
Assert-True ($guest.Status -eq 401) 'закрытый метод отвечает 401' "ожидался 401, получен $($guest.Status)"

Write-Step 'Пользователь'
# 409 — норма: логин уже заведён, иначе проверку нельзя было бы перезапускать.
try {
    Invoke-Api '/api/auth/register' 'POST' @{ login = $Login; password = $Password; displayName = 'Проверка' } | Out-Null
    Write-Note 'пользователь создан'
} catch {
    $registerStatus = [int]$_.Exception.Response.StatusCode
    if ($registerStatus -eq 409) {
        Write-Note 'логин уже существует — это нормально, скрипт можно перезапускать'
    } elseif ($registerStatus -eq 400) {
        Write-Bad 'регистрация отвергнута: пароль короче 8 символов'
        exit 1
    } else {
        Write-Bad "регистрация вернула $registerStatus"
        exit 1
    }
}

$me = Invoke-Api '/api/auth/login' 'POST' @{ login = $Login; password = $Password }
Assert-True ($me.login -eq $Login) "вошёл как $Login" "вошёл как $($me.login)"
$sessionCookie = ($session.Cookies.GetCookies($Base) | Where-Object { $_.Name -eq 'JSESSIONID' })
Assert-True ($null -ne $sessionCookie) 'выдана кука сессии' 'кука сессии не выдана'

$meRead = Invoke-Api '/api/auth/me'
Assert-True ($meRead.login -eq $Login) 'GET /me отвечает тем же пользователем' 'GET /me вернул другого пользователя'

Write-Step 'Реквизиты маркетплейса'
$credentials = Read-OzonCredentials
if ($SkipCredentials) {
    Write-Note 'подключение пропущено (-SkipCredentials)'
} elseif (-not $credentials) {
    Write-Bad 'в .env нет OZON_CLIENT_ID и OZON_API_KEY — подключение проверить нечем'
    $script:Failures++
} else {
    $connected = Invoke-Api '/api/marketplaces/ozon/credentials' 'PUT' `
        @{ clientId = $credentials.ClientId; apiKey = $credentials.ApiKey }
    Assert-True ($connected.marketplace -eq 'OZON') "реквизиты приняты (clientId=$($connected.clientId))" 'реквизиты не приняты'
    Assert-True ($null -eq $connected.PSObject.Properties['apiKey']) 'секретный ключ не возвращается' 'ответ содержит apiKey'
}

$marketplaces = Invoke-Api '/api/marketplaces'
$ozon = $marketplaces | Where-Object { $_.code -eq 'OZON' }
Assert-True ($null -ne $ozon) 'OZON есть в справочнике' 'OZON не найден'
Assert-True ($ozon.connected) 'OZON подключён' 'OZON не подключён — задайте реквизиты'
Assert-True ($null -eq $marketplaces[0].PSObject.Properties['apiKey']) 'в справочнике нет apiKey' 'в справочнике есть apiKey'

if ($RunImports) {
    Write-Step 'Импорты'
    $catalogJob = Invoke-Api '/api/marketplaces/ozon/imports/catalog' 'POST'
    $catalog = Wait-Import -Id $catalogJob.id
    Assert-True ($catalog.status -eq 'DONE') "каталог загружен (прогон $($catalog.id))" "каталог: $($catalog.status) $($catalog.error)"

    $financeJob = Invoke-Api "/api/marketplaces/ozon/imports/finance?dateFrom=$DateFrom&dateTo=$DateTo&refreshAccrualTypes=true" 'POST'
    $finance = Wait-Import -Id $financeJob.id
    Assert-True ($finance.status -eq 'DONE') "финансы загружены (прогон $($finance.id))" "финансы: $($finance.status) $($finance.error)"
}

Write-Step 'Покрытие периода'
$coverage = Invoke-Api "/api/marketplaces/ozon/data/coverage?dateFrom=$DateFrom&dateTo=$DateTo"
Assert-True ($coverage.failedDays -eq 0) "дней с ошибками: $($coverage.failedDays)" "дней с ошибками: $($coverage.failedDays)"

$daily = Invoke-Api "/api/marketplaces/ozon/analytics/daily?dateFrom=$DateFrom&dateTo=$DateTo"
$hasData = $coverage.loadedDays -gt 0

if ($hasData) {
    Assert-True $true "загружено дней: $($coverage.loadedDays) из $($coverage.requestedDays)" ''
    if ($coverage.missingDays -and $coverage.missingDays.Count -gt 0) {
        Write-Note "не загружены дни: $($coverage.missingDays -join ', ')"
    }
    Assert-True ($daily.status -eq 'READY') "отчёт за период полный: $($daily.status)" "отчёт за период: $($daily.status)"
} else {
    # Пустая база — не поломка. Проверять надо честность ответа: раз данных нет,
    # приложение обязано сказать об этом прямо, а не показать нули как будто
    # продаж не было.
    Assert-True ($daily.status -eq 'NOT_LOADED') `
        'данных нет, и приложение честно сообщило NOT_LOADED' `
        "данных нет, но отчёт утверждает $($daily.status) — нули выглядели бы как «продаж не было»"
    Write-Note 'база пуста. Чтобы загрузить период: .\scripts\check-api.ps1 -RunImports'
}

$dailyDiff = [math]::Round((Get-Number $daily.income) - (Get-Number $daily.expenses) - (Get-Number $daily.payout), 2)
Assert-True ($dailyDiff -eq 0) `
    "тождество доходы − расходы = выплате сходится (доходы $($daily.income), расходы $($daily.expenses), выплата $($daily.payout))" `
    "тождество не сошлось на $dailyDiff — расходы потеряны или задвоены"
Assert-True ($daily.reconciled) 'признак reconciled подтверждён сервером' 'сервер не подтвердил сверку'

if ($hasData) {
    # Количество проверяется отдельно от денег намеренно: неверный счёт единиц не портит
    # тождество выше, поэтому деньги остались бы верными и при неверном количестве.
    $sold = [int]($daily.total.soldQuantity)
    $returned = [int]($daily.total.returnedQuantity)
    Assert-True ($sold -ge 0 -and $returned -ge 0) `
        "количество неотрицательное: продано $sold, возвращено $returned" `
        "количество отрицательное — количество не может быть отрицательным"
    Assert-True ($sold -gt 0) `
        "за период что-то продано: $sold единиц" `
        "за период продано ноль единиц, хотя выручка $($daily.total.sales) — количество считается неверно"
}

Write-Step 'Отчёт по товарам'
$products = Invoke-Api "/api/marketplaces/ozon/analytics/products?dateFrom=$DateFrom&dateTo=$DateTo&size=5"

if ($hasData) {
    Assert-True ($products.catalog.loaded) "каталог загружен: $($products.catalog.products) товаров" 'каталог не загружен'
    Assert-True ($products.totalRows -eq $products.catalog.products) `
        "в отчёте все товары каталога: $($products.totalRows)" `
        "в отчёте $($products.totalRows) строк, а в каталоге $($products.catalog.products)"
    Assert-True ($products.totals.income -eq $daily.income) `
        "доходы совпали с дневным отчётом: $($products.totals.income)" `
        "доходы разошлись: товары $($products.totals.income), дни $($daily.income)"

    # Количество считается двумя разными путями: в дневном отчёте свёрткой строк,
    # в товарном — агрегатом SQL по SKU. Расхождение означало бы, что одно из двух врёт.
    Assert-True ($products.totals.soldQuantity -eq $daily.total.soldQuantity) `
        "количество продаж совпало с дневным отчётом: $($products.totals.soldQuantity)" `
        "количество разошлось: товары $($products.totals.soldQuantity), дни $($daily.total.soldQuantity)"
    Assert-True ($products.totals.returnedQuantity -eq $daily.total.returnedQuantity) `
        "количество возвратов совпало с дневным отчётом: $($products.totals.returnedQuantity)" `
        "возвраты разошлись: товары $($products.totals.returnedQuantity), дни $($daily.total.returnedQuantity)"

    $unallocated = Get-Number $products.unallocatedExpenses
    $productExpenses = [math]::Round((Get-Number $products.totals.expenses) + $unallocated, 2)
    $dayExpenses = [math]::Round((Get-Number $daily.expenses), 2)
    Assert-True ([math]::Abs($productExpenses - $dayExpenses) -lt 0.01) `
        "расходы сходятся: по товарам $($products.totals.expenses) + без SKU $unallocated = $productExpenses, по дням $dayExpenses" `
        "расходы разошлись на $([math]::Round($productExpenses - $dayExpenses, 2)) — часть потерялась или задвоилась"

    if ($products.skusMissingFromCatalog -and $products.skusMissingFromCatalog.Count -gt 0) {
        Write-Note "SKU из начислений без карточки в каталоге: $($products.skusMissingFromCatalog.Count)"
    }
} else {
    Assert-True ($products.totalRows -eq 0) `
        'без каталога отчёт по товарам честно пуст' `
        "без каталога отчёт вернул $($products.totalRows) строк"
}

Write-Step 'Итог'
if ($script:Failures -eq 0) {
    Write-Host "  проверок: $script:Checks, провалов: 0" -ForegroundColor Green
    Write-Host "  API работает и отдаёт сходящиеся цифры." -ForegroundColor Green
    exit 0
} else {
    Write-Host "  проверок: $script:Checks, провалов: $script:Failures" -ForegroundColor Red
    exit 1
}