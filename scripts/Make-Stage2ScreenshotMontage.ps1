param([string]$Size='390x844')
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$taskRoot=Split-Path -Parent $PSScriptRoot
$folder=Join-Path $taskRoot 'screenshots/stage2'
$names=@('00-welcome','07-login-unconfigured','08-registration','02-settings-account','04-ai-key-masked','06-history')
$labels=@('Приветствие','Вход','Регистрация','Настройки','Личный ключ ИИ','История оригиналов')
$canvas=[System.Drawing.Bitmap]::new(1424,535)
$graphics=[System.Drawing.Graphics]::FromImage($canvas)
$font=[System.Drawing.Font]::new('Segoe UI',10)
try {
    $graphics.Clear([System.Drawing.Color]::FromArgb(227,223,238))
    $graphics.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    for($i=0;$i -lt $names.Count;$i++) {
        $source=[System.Drawing.Image]::FromFile((Join-Path $folder "$Size/$($names[$i]).png"))
        try {
            $graphics.DrawString($labels[$i],$font,[System.Drawing.Brushes]::Black,14+235*$i,7)
            $graphics.DrawImage($source,[System.Drawing.Rectangle]::new(14+235*$i,30,221,478))
        } finally {$source.Dispose()}
    }
    $canvas.Save((Join-Path $folder "new-screens-$Size.png"),[System.Drawing.Imaging.ImageFormat]::Png)
} finally {$font.Dispose();$graphics.Dispose();$canvas.Dispose()}
