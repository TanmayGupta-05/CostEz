$ErrorActionPreference = 'Stop'
$env:DEBUG = 'false'
Push-Location $PSScriptRoot
try {
  Push-Location frontend
  npm.cmd ci --no-audit --no-fund
  if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed' }
  npm.cmd run build
  if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed' }
  Pop-Location
  New-Item -ItemType Directory -Path backend\src\main\resources\static -Force | Out-Null
  Copy-Item frontend\dist\* backend\src\main\resources\static -Recurse -Force
  Push-Location backend
  $localMaven = Join-Path $PSScriptRoot 'tools\apache-maven-3.9.11\bin\mvn.cmd'
  if (Test-Path -LiteralPath $localMaven) { & $localMaven -B package } else { mvn.cmd -B package }
  if ($LASTEXITCODE -ne 0) { throw 'Backend build or tests failed' }
  Pop-Location
  New-Item -ItemType Directory -Path release -Force | Out-Null
  Copy-Item backend\target\costez-1.0.0.jar release\costez.jar -Force
  Copy-Item Start-Costez.cmd release\Start-Costez.cmd -Force
  Write-Host 'Build complete. Run release\Start-Costez.cmd and visit http://localhost:8080'
} finally { Pop-Location }
