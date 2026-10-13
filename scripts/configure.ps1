$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$destination = Join-Path $projectRoot '.env'
if (Test-Path -LiteralPath $destination) { throw '.env already exists. Edit it directly to preserve your database password and encryption key.' }
$keyBytes = New-Object byte[] 32
$dbBytes = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($keyBytes)
$rng.GetBytes($dbBytes)
$rng.Dispose()
$content = [IO.File]::ReadAllText((Join-Path $projectRoot '.env.example'))
$content = $content.Replace('replace-with-a-long-random-database-password', [Convert]::ToBase64String($dbBytes))
$content = $content.Replace('replace-with-a-base64-encoded-32-byte-key', [Convert]::ToBase64String($keyBytes))
[IO.File]::WriteAllText($destination, $content, (New-Object Text.UTF8Encoding($false)))
Write-Host 'Created .env with random database and encryption secrets. Edit domain and integration settings before deployment. Keep this file private.'
