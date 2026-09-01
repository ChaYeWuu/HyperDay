# Generates static widget preview images (MIUI picker ignores previewLayout,
# it only shows android:previewImage; without it the app icon is shown).
# Drawn to match the real widget layouts: HyperOS white rounded card,
# blue day numbers, gray secondary text.
Add-Type -AssemblyName System.Drawing

$outDir = "C:\Users\chaye\HyperMatter\app\src\main\res\drawable-nodpi"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$Primary = [System.Drawing.Color]::FromArgb(0xFF, 0x1C, 0x1C, 0x1E)
$Secondary = [System.Drawing.Color]::FromArgb(0xFF, 0x8C, 0x8C, 0x8C)
$Accent = [System.Drawing.Color]::FromArgb(0xFF, 0x34, 0x82, 0xFF)
$Border = [System.Drawing.Color]::FromArgb(0xFF, 0xEC, 0xEC, 0xEF)

function New-Canvas([int]$w, [int]$h) {
    $bmp = New-Object System.Drawing.Bitmap($w, $h)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAlias
    $g.Clear([System.Drawing.Color]::Transparent)
    # rounded card with a hairline border so it reads on the light picker
    $r = 64
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $p.AddArc(0, 0, 2 * $r, 2 * $r, 180, 90)
    $p.AddArc($w - 2 * $r, 0, 2 * $r, 2 * $r, 270, 90)
    $p.AddArc($w - 2 * $r, $h - 2 * $r, 2 * $r, 2 * $r, 0, 90)
    $p.AddArc(0, $h - 2 * $r, 2 * $r, 2 * $r, 90, 90)
    $p.CloseFigure()
    $bg = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
    $g.FillPath($bg, $p)
    $pen = New-Object System.Drawing.Pen($Border, 2)
    $g.DrawPath($pen, $p)
    return @($bmp, $g)
}

function Font([string]$name, [float]$size, [System.Drawing.FontStyle]$style) {
    New-Object System.Drawing.Font($name, $size, $style, [System.Drawing.GraphicsUnit]::Pixel)
}

$YaHei = 'Microsoft YaHei UI'

# ---------------------------------------------------------------- card 2x2
$bmp, $g = New-Canvas 600 600
$pad = 60
$bPrimary = New-Object System.Drawing.SolidBrush($Primary)
$bSecondary = New-Object System.Drawing.SolidBrush($Secondary)
$bAccent = New-Object System.Drawing.SolidBrush($Accent)

$fTitle = Font $YaHei 42 ([System.Drawing.FontStyle]::Bold)
$fDate = Font $YaHei 30 ([System.Drawing.FontStyle]::Regular)
$fNum = Font $YaHei 132 ([System.Drawing.FontStyle]::Bold)
$fUnit = Font $YaHei 34 ([System.Drawing.FontStyle]::Regular)

$g.DrawString('生日', $fTitle, $bPrimary, $pad, 58)
$g.DrawString('2月14日 周五', $fDate, $bSecondary, $pad, 116)

$numText = '12'
$numSize = $g.MeasureString($numText, $fNum)
$unitSize = $g.MeasureString('天', $fUnit)
$ny = 600 - 84 - $numSize.Height
$g.DrawString($numText, $fNum, $bAccent, $pad, $ny)
$g.DrawString('天', $fUnit, $bSecondary, ($pad + $numSize.Width + 12), ($ny + $numSize.Height - $unitSize.Height))

$bmp.Save("$outDir\widget_card_preview.png", [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()

# ---------------------------------------------------------------- list 4x2
$bmp, $g = New-Canvas 1200 600
$pad = 60
$fHeader = Font $YaHei 27 ([System.Drawing.FontStyle]::Bold)
$fRowTitle = Font $YaHei 33 ([System.Drawing.FontStyle]::Bold)
$fRowDate = Font $YaHei 26 ([System.Drawing.FontStyle]::Regular)
$fRowNum = Font $YaHei 42 ([System.Drawing.FontStyle]::Bold)
$fRowUnit = Font $YaHei 25 ([System.Drawing.FontStyle]::Regular)

$g.DrawString('即将到来', $fHeader, $bSecondary, $pad, 54)

$rows = @(
    @('发工资', '每月15日', '12'),
    @('春节', '2月17日', '45'),
    @('生日', '2月14日', '89'),
    @('去上海', '10月1日', '123')
)
$y = 116
$rowH = 108
foreach ($row in $rows) {
    $cy = $y + $rowH / 2
    $ts = $g.MeasureString($row[0], $fRowTitle)
    $g.DrawString($row[0], $fRowTitle, $bPrimary, $pad, ($cy - $ts.Height / 2))
    $ds = $g.MeasureString($row[1], $fRowDate)
    $g.DrawString($row[1], $fRowDate, $bSecondary, ($pad + $ts.Width + 16), ($cy - $ds.Height / 2))
    # right-aligned "N 天"
    $us = $g.MeasureString('天', $fRowUnit)
    $ns = $g.MeasureString($row[2], $fRowNum)
    $rightEdge = 1200 - $pad
    $g.DrawString('天', $fRowUnit, $bSecondary, ($rightEdge - $us.Width), ($cy - $us.Height / 2))
    $g.DrawString($row[2], $fRowNum, $bAccent, ($rightEdge - $us.Width - 8 - $ns.Width), ($cy - $ns.Height / 2))
    $y += $rowH
}

$bmp.Save("$outDir\widget_list_preview.png", [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()

# ---------------------------------------------------------------- minimal 2x1
$bmp, $g = New-Canvas 600 300
$pad = 60
$fNum = Font $YaHei 86 ([System.Drawing.FontStyle]::Bold)
$fUnit = Font $YaHei 27 ([System.Drawing.FontStyle]::Regular)
$fTitle = Font $YaHei 32 ([System.Drawing.FontStyle]::Bold)

$numText = '12'
$ns = $g.MeasureString($numText, $fNum)
$us = $g.MeasureString('天', $fUnit)
$ts = $g.MeasureString('去上海', $fTitle)
$ny = (300 - $ns.Height) / 2
$g.DrawString($numText, $fNum, $bAccent, $pad, $ny)
$g.DrawString('天', $fUnit, $bSecondary, ($pad + $ns.Width + 8), ($ny + $ns.Height - $us.Height))
$g.DrawString('去上海', $fTitle, $bPrimary, ($pad + $ns.Width + 8 + $us.Width + 26), ((300 - $ts.Height) / 2))

$bmp.Save("$outDir\widget_minimal_preview.png", [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()

Get-ChildItem $outDir\widget_*_preview.png | Select-Object Name, Length
