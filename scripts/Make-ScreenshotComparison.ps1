param([string]$Size = '390x844')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$taskRoot = Split-Path -Parent $PSScriptRoot
$names = @('01-tasks', '02-thoughts', '03-capture', '04-calendar', '05-overview')
$sheet = New-Object System.Drawing.Bitmap 1303,580
$graphics = [System.Drawing.Graphics]::FromImage($sheet)
$graphics.Clear([System.Drawing.Color]::FromArgb(222,219,233))
$graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
for ($i=0; $i -lt $names.Count; $i++) {
    $source = [System.Drawing.Image]::FromFile((Join-Path $taskRoot "screenshots/$Size/$($names[$i]).png"))
    try { $graphics.DrawImage($source, [System.Drawing.Rectangle]::new(14 + 255*$i,22,245,530)) }
    finally { $source.Dispose() }
}
$graphics.Dispose()
$sheet.Save((Join-Path $taskRoot 'screenshots/native-all-screens.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$comparison = New-Object System.Drawing.Bitmap 1303,1160
$graphics = [System.Drawing.Graphics]::FromImage($comparison)
$reference = [System.Drawing.Image]::FromFile((Join-Path $taskRoot 'reference/v14_all_screens.png'))
$graphics.DrawImage($reference,0,0,1303,580)
$graphics.DrawImage($sheet,0,580,1303,580)
$graphics.Dispose()
$reference.Dispose()
$sheet.Dispose()
$comparison.Save((Join-Path $taskRoot 'screenshots/reference-vs-native.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$comparison.Dispose()
