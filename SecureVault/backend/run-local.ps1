$ErrorActionPreference = 'Stop'

$mysqlServices = @(Get-Service -Name '*mysql*' -ErrorAction SilentlyContinue)
if ($mysqlServices.Count -eq 0) {
    Write-Error 'MYSQL_NOT_INSTALLED. Install MySQL Server 8.0, then run this script again.'
    exit 1
}

$mysqlService = $mysqlServices | Where-Object Status -eq 'Running' | Select-Object -First 1
if (-not $mysqlService) {
    $mysqlService = $mysqlServices | Select-Object -First 1
    try {
        Start-Service -Name $mysqlService.Name
        $mysqlService.WaitForStatus('Running', [TimeSpan]::FromSeconds(20))
    } catch {
        Write-Error "MYSQL_NOT_RUNNING. Start the service from an elevated PowerShell with: Start-Service '$($mysqlService.Name)'"
        exit 1
    }
}

$securePassword = Read-Host 'Enter the local MySQL root password (input is hidden)' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $securePassword).Password

Push-Location $PSScriptRoot
try {
    & .\mvnw.cmd spring-boot:run
    exit $LASTEXITCODE
} finally {
    Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
    Pop-Location
}