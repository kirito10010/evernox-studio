# Fetch Code Arena leaderboard pages and pack them into one zip.
#
# WHY THIS EXISTS:
#   The production server (Tencent Cloud datacenter IP) is rejected by arena.ai's
#   CDN/WAF with HTTP 403, so the backend cannot crawl the site by itself.
#   Run this script on a machine that CAN open https://arena.ai in a browser,
#   then upload the produced zip on the leaderboard page ("import package").
#
#   Parsing stays on the server (AiArenaCrawler), this script only downloads.
#
# USAGE: double-click fetch-arena-leaderboard.bat

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$Base = 'https://arena.ai/leaderboard/code/webdev/'

# Must stay in sync with AiArenaCrawler.CATEGORIES in the backend.
$Slugs = @(
    'overall',
    'fullstack',
    'frontend',
    'html',
    'react',
    'brand-marketing',
    'reference-based-design',
    'data-analytics',
    'consumer-product',
    'gaming',
    'simulations',
    'content-creation-tools'
)

$Headers = @{
    'User-Agent'      = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36'
    'Accept'          = 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8'
    'Accept-Language' = 'en-US,en;q=0.9'
}

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$Stamp = Get-Date -Format 'yyyyMMdd-HHmm'
$WorkDir = Join-Path $env:TEMP "arena-lb-$Stamp"
$ZipPath = Join-Path $ScriptDir "arena-lb-$Stamp.zip"

New-Item -ItemType Directory -Path $WorkDir -Force | Out-Null

Write-Host ''
Write-Host "Downloading $($Slugs.Count) category pages from arena.ai ..." -ForegroundColor Cyan

$failed = @()
foreach ($slug in $Slugs) {
    $file = Join-Path $WorkDir "$slug.html"
    try {
        Invoke-WebRequest -Uri "$Base$slug" -Headers $Headers -UseBasicParsing -TimeoutSec 60 -OutFile $file
        $text = Get-Content $file -Raw
        if ($text.Length -lt 50000 -or $text.IndexOf('<table') -lt 0) {
            $failed += $slug
            Write-Host ("  [bad]  {0} - page looks incomplete, skipped" -f $slug) -ForegroundColor Yellow
            Remove-Item $file -Force -ErrorAction SilentlyContinue
        } else {
            Write-Host ("  [ok]   {0}" -f $slug)
        }
    } catch {
        $failed += $slug
        Write-Host ("  [fail] {0} - {1}" -f $slug, $_.Exception.Message) -ForegroundColor Yellow
    }
}

$okCount = $Slugs.Count - $failed.Count
if ($okCount -eq 0) {
    Write-Host ''
    Write-Host 'All downloads failed: this machine cannot reach arena.ai either.' -ForegroundColor Red
    Write-Host 'Nothing was packaged.' -ForegroundColor Red
    exit 1
}

if (Test-Path $ZipPath) { Remove-Item $ZipPath -Force }
Compress-Archive -Path (Join-Path $WorkDir '*.html') -DestinationPath $ZipPath -CompressionLevel Optimal

$sizeMb = [math]::Round((Get-Item $ZipPath).Length / 1MB, 2)

Write-Host ''
Write-Host ("Package ready: {0}" -f $ZipPath) -ForegroundColor Green
Write-Host ("  {0}/{1} categories, {2} MB" -f $okCount, $Slugs.Count, $sizeMb) -ForegroundColor Green
if ($failed.Count -gt 0) {
    Write-Host ("  Missing (old data will be kept for them): {0}" -f ($failed -join ', ')) -ForegroundColor Yellow
}
Write-Host ''
Write-Host 'Next: open the website -> AI menu -> Model Leaderboard ->' -ForegroundColor Cyan
Write-Host '      click "Import package" and select the zip file above.' -ForegroundColor Cyan
Write-Host ''
