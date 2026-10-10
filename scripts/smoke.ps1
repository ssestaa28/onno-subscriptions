param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$Username = 'admin',
    [string]$Password = $(if ($env:APP_ADMIN_PASSWORD) { $env:APP_ADMIN_PASSWORD } else { 'admin' })
)

$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$script:checks = 0

function Api([string]$Method, [string]$Path, $Body = $null) {
    $params = @{
        Uri = "$BaseUrl$Path"; Method = $Method; WebSession = $session
        UseBasicParsing = $true; TimeoutSec = 30
    }
    if ($Method -ne 'Get') {
        $csrf = Api Get '/api/auth/csrf'
        $params.Headers = @{ 'X-XSRF-TOKEN' = $csrf.token }
        $params.ContentType = 'application/json; charset=utf-8'
        $json = if ($null -eq $Body) { '{}' } else { ConvertTo-Json -InputObject $Body -Depth 20 -Compress }
        $params.Body = [System.Text.Encoding]::UTF8.GetBytes($json)
    }
    $response = Invoke-WebRequest @params
    $text = [System.Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray())
    if ($text) {
        $parsed = ConvertFrom-Json -InputObject $text
        return $parsed
    }
}

function Check([bool]$Condition, [string]$Name) {
    if (-not $Condition) { throw "FAILED: $Name" }
    $script:checks++
    Write-Host "PASS: $Name"
}

function Form([string]$Kind, [string]$Name) {
    $descriptor = Api Get "/api/divkit/$Kind/$Name/new"
    return $descriptor.card.states[0].div.items[0].custom_props.form
}

function ExpectRejected([scriptblock]$Operation, [string]$Name) {
    $rejected = $false
    try { & $Operation | Out-Null }
    catch {
        if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 409) { $rejected = $true }
        else { throw }
    }
    Check $rejected $Name
}

$login = Api Post '/api/auth/login' @{ username = $Username; password = $Password }
Check ($login.authenticated -eq $true) 'Authentication and CSRF'

$shell = Api Get '/api/divkit/shell'
Check ($null -ne $shell) 'Sidebar descriptor'
foreach ($route in @('/api/divkit/home', '/api/divkit/catalogs/Clients', '/api/divkit/catalogs/Tariffs',
        '/api/divkit/documents/Payments', '/api/divkit/documents/Subscriptions',
        '/api/divkit/registers/PersonalAccounts', '/api/divkit/registers/TariffRevenue')) {
    Check ($null -ne (Api Get $route)) "UI descriptor $route"
}
$clientForm = Form 'catalogs' 'Clients'
$paymentForm = Form 'documents' 'Payments'
$subscriptionForm = Form 'documents' 'Subscriptions'
Check (($subscriptionForm.meta.attributes | Where-Object fieldName -eq 'status').visibleInForm -ne $false) 'Required subscription status is available to the form'
Check ($null -ne $subscriptionForm.initial.startsAt) 'New subscription has start date default'

$suffix = [Guid]::NewGuid().ToString('N').Substring(0, 8)
$client = Api Post '/api/catalogs/Clients' @{
    description = "Smoke client $suffix"; email = 'smoke@example.com'; phone = '+7 999 000-00-00'
    status = $clientForm.initial.status; registeredAt = $clientForm.initial.registeredAt
}
$monthly = Api Post '/api/catalogs/Tariffs' @{
    description = "Monthly $suffix"; price = 100; periodDays = 30; available = $true
}
$annual = Api Post '/api/catalogs/Tariffs' @{
    description = "Annual $suffix"; price = 900; periodDays = 365; available = $true
}
Check ($null -ne $client.id -and $null -ne $monthly.id -and $null -ne $annual.id) 'Catalog creation through generated API'

$payment = Api Post '/api/documents/Payments' @{
    client = $client.id; amount = 1500; method = $paymentForm.initial.method
}
$payment = Api Post "/api/documents/Payments/$($payment.id)/post"
Check ($payment.posted -eq $true) 'Payment posting'
Check ($null -ne $payment.date) 'Payment document date is assigned automatically'
$balances = @(Api Get "/api/registers/PersonalAccounts/balance?client=$($client.id)")
Check ($balances.Count -eq 1 -and [decimal]$balances[0].amount -eq 1500) 'Payment credits 1500 RUB'

$subscription = Api Post '/api/documents/Subscriptions' @{
    client = $client.id; status = $subscriptionForm.initial.status
    startsAt = '2026-10-01'
    lines = @(@{tariff = $monthly.id; periods = 2}, @{tariff = $annual.id; periods = 1})
}
Check ([decimal]$subscription.total -eq 1100) 'Server calculates total and tariff prices'
Check ($null -ne $subscription.date) 'Subscription document date is assigned automatically'
Check ($subscription.endsAt -eq '2027-10-01') 'Longest line determines subscription end'
$subscription = Api Post "/api/documents/Subscriptions/$($subscription.id)/post"
Check ($subscription.posted -eq $true) 'Subscription posting'
$balances = @(Api Get "/api/registers/PersonalAccounts/balance?client=$($client.id)")
Check ([decimal]$balances[0].amount -eq 400) 'Subscription debits 1100 RUB'
$rows = @(Api Get "/api/registers/TariffRevenue/turnover?from=2000-01-01&to=2100-01-01&client=$($client.id)")
$sum = ($rows | Measure-Object -Property amount -Sum).Sum
Check ($rows.Count -eq 2 -and [decimal]$sum -eq 1100) 'Revenue is split by tariff and client'

$expensive = Api Post '/api/documents/Subscriptions' @{
    client = $client.id; status = $subscriptionForm.initial.status
    startsAt = '2026-10-01'
    lines = @(@{tariff = $annual.id; periods = 1})
}
ExpectRejected { Api Post "/api/documents/Subscriptions/$($expensive.id)/post" } 'Insufficient funds rejects posting'
$balances = @(Api Get "/api/registers/PersonalAccounts/balance?client=$($client.id)")
Check ([decimal]$balances[0].amount -eq 400) 'Rejected posting preserves balance'
$saved = Api Get "/api/documents/Subscriptions/$($expensive.id)"
Check ($saved.posted -eq $false) 'Rejected subscription remains unposted'

$actions = Api Get '/api/actions/documents/Subscriptions'
Check (($actions | ConvertTo-Json -Depth 10) -match 'cancel') 'Cancellation action descriptor'
Api Post "/api/actions/documents/Subscriptions/cancel?id=$($subscription.id)" @{ inputs = @{ reason = 'HTTP smoke cancellation' } } | Out-Null
$saved = Api Get "/api/documents/Subscriptions/$($subscription.id)"
Check ($saved.cancellationReason -eq 'HTTP smoke cancellation' -and $saved.recognized -eq $false) 'Cancellation persists reason and removes revenue recognition'
$balances = @(Api Get "/api/registers/PersonalAccounts/balance?client=$($client.id)")
Check ([decimal]$balances[0].amount -eq 1500) 'Cancellation returns 1100 RUB'
$rows = @(Api Get "/api/registers/TariffRevenue/turnover?from=2000-01-01&to=2100-01-01&client=$($client.id)")
$sum = ($rows | Measure-Object -Property amount -Sum).Sum
Check ([decimal]$sum -eq 0) 'Cancelled subscription has zero revenue'
Write-Host "HTTP smoke successful: $script:checks checks. Test client: $($client.id)"
