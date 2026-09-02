$d = Get-Content widget_dump_raw.xml -Raw
[xml]$x = $d
function Walk($n) {
    if ($n.'resource-id' -eq 'com.miui.home:id/widget_container') {
        Write-Output ("container bounds=" + $n.bounds)
    }
    foreach ($c in $n.ChildNodes) { Walk $c }
}
Walk $x.DocumentElement
