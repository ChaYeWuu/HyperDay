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
# Merged card widget: tag pill (距离/过去) top-left, title + date,
# big centered day number.
$bmp, $g = New-Canvas 600 600
$pad = 60
$bPrimary = New-Object System.Drawing.SolidBrush($Primary)
$bSecondary = New-Object System.Drawing.SolidBrush($Secondary)
$bAccent = New-Object System.Drawing.SolidBrush($Accent)

$fTag = Font $YaHei 26 ([System.Drawing.FontStyle]::Regular)
$fTitle = Font $YaHei 50 ([System.Drawing.FontStyle]::Bold)
$fDate = Font $YaHei 30 ([System.Drawing.FontStyle]::Regular)
$fNum = Font $YaHei 130 ([System.Drawing.FontStyle]::Bold)
$fUnit = Font $YaHei 36 ([System.Drawing.FontStyle]::Regular)

# tag pill (rounded 18px, 8% black)
$tagText = '距离'
$tagSize = $g.MeasureString($tagText, $fTag)
$tagX = $pad
$tagY = 46
$tagW = $tagSize.Width + 32
$tagH = $tagSize.Height + 20
$tagPath = New-Object System.Drawing.Drawing2D.GraphicsPath
$r = 18
$tagPath.AddArc($tagX, $tagY, 2 * $r, 2 * $r, 180, 90)
$tagPath.AddArc($tagX + $tagW - 2 * $r, $tagY, 2 * $r, 2 * $r, 270, 90)
$tagPath.AddArc($tagX + $tagW - 2 * $r, $tagY + $tagH - 2 * $r, 2 * $r, 2 * $r, 0, 90)
$tagPath.AddArc($tagX, $tagY + $tagH - 2 * $r, 2 * $r, 2 * $r, 90, 90)
$tagPath.CloseFigure()
$tagBg = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(0x14, 0x00, 0x00, 0x00))
$g.FillPath($tagBg, $tagPath)
$g.DrawString($tagText, $fTag, $bSecondary, ($tagX + 16), ($tagY + 9))

$g.DrawString('生日', $fTitle, $bPrimary, $pad, ($tagY + $tagH + 22))
$g.DrawString('2月14日 周五', $fDate, $bSecondary, $pad, ($tagY + $tagH + 84))

$numText = '12'
$numSize = $g.MeasureString($numText, $fNum)
$unitSize = $g.MeasureString('天', $fUnit)
$blockW = $numSize.Width + 14 + $unitSize.Width
$bx = (600 - $blockW) / 2
$top = 210
$bottom = 600 - 60
$by = $top + ($bottom - $top - $numSize.Height) / 2
$g.DrawString($numText, $fNum, $bAccent, $bx, $by)
$g.DrawString('天', $fUnit, $bSecondary, ($bx + $numSize.Width + 14), ($by + $numSize.Height - $unitSize.Height))

$bmp.Save("$outDir\widget_card_preview.png", [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()

# ---------------------------------------------------------------- list 4x2
# Rows carry their own 距离/过去 tag pill (no header).
$bmp, $g = New-Canvas 1200 600
$pad = 60
$fTag = Font $YaHei 24 ([System.Drawing.FontStyle]::Regular)
$fRowTitle = Font $YaHei 38 ([System.Drawing.FontStyle]::Bold)
$fRowDate = Font $YaHei 30 ([System.Drawing.FontStyle]::Regular)
$fRowNum = Font $YaHei 48 ([System.Drawing.FontStyle]::Bold)
$fRowUnit = Font $YaHei 28 ([System.Drawing.FontStyle]::Regular)

# rows: title, date, days, tag (距离/过去)
$rows = @(
    @('发工资', '每月15日', '12', '距离'),
    @('春节', '2月17日', '45', '距离'),
    @('生日', '2月14日', '89', '距离'),
    @('高考', '6月7日', '123', '过去')
)
$y = 60
$rowH = 135
foreach ($row in $rows) {
    $cy = $y + $rowH / 2
    # tag pill (rounded 14px, 8% black)
    $tagSize = $g.MeasureString($row[3], $fTag)
    $tagW = $tagSize.Width + 24
    $tagH = $tagSize.Height + 14
    $tagPath = New-Object System.Drawing.Drawing2D.GraphicsPath
    $r = 14
    $tagPath.AddArc($pad, ($cy - $tagH / 2), 2 * $r, 2 * $r, 180, 90)
    $tagPath.AddArc(($pad + $tagW - 2 * $r), ($cy - $tagH / 2), 2 * $r, 2 * $r, 270, 90)
    $tagPath.AddArc(($pad + $tagW - 2 * $r), ($cy + $tagH / 2 - 2 * $r), 2 * $r, 2 * $r, 0, 90)
    $tagPath.AddArc($pad, ($cy + $tagH / 2 - 2 * $r), 2 * $r, 2 * $r, 90, 90)
    $tagPath.CloseFigure()
    $tagBg = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(0x14, 0x00, 0x00, 0x00))
    $g.FillPath($tagBg, $tagPath)
    $g.DrawString($row[3], $fTag, $bSecondary, ($pad + 12), ($cy - $tagSize.Height / 2))
    # title after the pill, then date
    $tx = $pad + $tagW + 14
    $ts = $g.MeasureString($row[0], $fRowTitle)
    $g.DrawString($row[0], $fRowTitle, $bPrimary, $tx, ($cy - $ts.Height / 2))
    $ds = $g.MeasureString($row[1], $fRowDate)
    $g.DrawString($row[1], $fRowDate, $bSecondary, ($tx + $ts.Width + 16), ($cy - $ds.Height / 2))
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
# 距离/过去 tag pill alone top-left; title vertically centered on the left;
# day number on the right, vertically centered.
$bmp, $g = New-Canvas 600 300
$pad = 50
$fNum = Font $YaHei 88 ([System.Drawing.FontStyle]::Bold)
$fUnit = Font $YaHei 28 ([System.Drawing.FontStyle]::Regular)
$fTitle = Font $YaHei 36 ([System.Drawing.FontStyle]::Bold)
$fTag = Font $YaHei 24 ([System.Drawing.FontStyle]::Regular)

$tagText = '距离'
$tagSize = $g.MeasureString($tagText, $fTag)
$tagW = $tagSize.Width + 24
$tagH = $tagSize.Height + 14
$tagY = 44
$tagPath = New-Object System.Drawing.Drawing2D.GraphicsPath
$r = 14
$tagPath.AddArc($pad, $tagY, 2 * $r, 2 * $r, 180, 90)
$tagPath.AddArc(($pad + $tagW - 2 * $r), $tagY, 2 * $r, 2 * $r, 270, 90)
$tagPath.AddArc(($pad + $tagW - 2 * $r), ($tagY + $tagH - 2 * $r), 2 * $r, 2 * $r, 0, 90)
$tagPath.AddArc($pad, ($tagY + $tagH - 2 * $r), 2 * $r, 2 * $r, 90, 90)
$tagPath.CloseFigure()
$tagBg = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(0x14, 0x00, 0x00, 0x00))
$g.FillPath($tagBg, $tagPath)
$g.DrawString($tagText, $fTag, $bSecondary, ($pad + 12), ($tagY + 7))

# title vertically centered on the left
$ts = $g.MeasureString('去上海', $fTitle)
$g.DrawString('去上海', $fTitle, $bPrimary, $pad, ((300 - $ts.Height) / 2))

# day number on the right, vertically centered
$numText = '12'
$ns = $g.MeasureString($numText, $fNum)
$us = $g.MeasureString('天', $fUnit)
$rightEdge = 600 - $pad
$ny = (300 - $ns.Height) / 2
$g.DrawString('天', $fUnit, $bSecondary, ($rightEdge - $us.Width), ($ny + $ns.Height - $us.Height))
$g.DrawString($numText, $fNum, $bAccent, ($rightEdge - $us.Width - 10 - $ns.Width), $ny)

$bmp.Save("$outDir\widget_minimal_preview.png", [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()

Get-ChildItem $outDir\widget_*_preview.png | Select-Object Name, Length
