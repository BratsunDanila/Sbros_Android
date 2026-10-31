param([string]$Size = '390x844')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$taskRoot = Split-Path -Parent $PSScriptRoot
$folder = Join-Path $taskRoot 'screenshots/v15-detail-fix'
function Write-Montage([string[]]$Names, [string]$FileName) {
    $canvas = [System.Drawing.Bitmap]::new(14 + 235 * $Names.Count, 545)
    $graphics = [System.Drawing.Graphics]::FromImage($canvas)
    $font = [System.Drawing.Font]::new('Segoe UI', 9)
    try {
        $graphics.Clear([System.Drawing.Color]::FromArgb(227,223,238))
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        for ($i=0; $i -lt $Names.Count; $i++) {
            $source = [System.Drawing.Image]::FromFile((Join-Path $folder "$Size/$($Names[$i]).png"))
            try {
                $graphics.DrawString($Names[$i], $font, [System.Drawing.Brushes]::Black, 14 + 235*$i, 8)
                $graphics.DrawImage($source, [System.Drawing.Rectangle]::new(14 + 235*$i, 30, 221, 478))
            } finally { $source.Dispose() }
        }
        $canvas.Save((Join-Path $folder $FileName), [System.Drawing.Imaging.ImageFormat]::Png)
    } finally { $font.Dispose(); $graphics.Dispose(); $canvas.Dispose() }
}
Write-Montage @('01-tasks','02-thoughts','03-capture','04-calendar','05-overview') 'all-screens.png'
Write-Montage @('projects-screen','project-detail','project-editor','task-editor-from-card','task-editor-from-arrow','material-editor') 'details-and-editors.png'
