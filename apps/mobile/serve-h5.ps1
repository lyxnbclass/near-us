$root = "D:\code\aicode-yy\apps\mobile\dist\build\h5"
$prefix = "http://127.0.0.1:5188/"

$listener = [System.Net.HttpListener]::new()
$listener.Prefixes.Add($prefix)
$listener.Start()
Write-Host "Serving $root at $prefix"

function Get-ContentType($path) {
  switch -Regex ($path) {
    "\.html$" { "text/html; charset=utf-8"; break }
    "\.js$" { "application/javascript; charset=utf-8"; break }
    "\.css$" { "text/css; charset=utf-8"; break }
    "\.json$" { "application/json; charset=utf-8"; break }
    "\.png$" { "image/png"; break }
    "\.jpg$|\.jpeg$" { "image/jpeg"; break }
    "\.svg$" { "image/svg+xml"; break }
    default { "application/octet-stream" }
  }
}

while ($listener.IsListening) {
  $context = $listener.GetContext()
  try {
    $relativePath = [Uri]::UnescapeDataString($context.Request.Url.AbsolutePath.TrimStart("/"))
    if ([string]::IsNullOrWhiteSpace($relativePath)) {
      $relativePath = "index.html"
    }
    $filePath = Join-Path $root $relativePath
    if (-not (Test-Path -LiteralPath $filePath -PathType Leaf)) {
      $filePath = Join-Path $root "index.html"
    }
    $bytes = [System.IO.File]::ReadAllBytes($filePath)
    $context.Response.StatusCode = 200
    $context.Response.ContentType = Get-ContentType $filePath
    $context.Response.OutputStream.Write($bytes, 0, $bytes.Length)
  } catch {
    $context.Response.StatusCode = 500
  } finally {
    $context.Response.OutputStream.Close()
  }
}
