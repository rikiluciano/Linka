param(
    [string]$Message = "Update Linka"
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path -LiteralPath $PSScriptRoot).Path
$serverAlias = "serveras"
$remoteRoot = "/home/ricardo/proyects/Linka"

function Invoke-Git {
    param([string[]]$GitArgs)
    $result = & git -c "safe.directory=$repoRoot" -C $repoRoot @GitArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Falló git $($GitArgs -join ' ')"
    }
    return $result
}

if ([string]::IsNullOrWhiteSpace($Message) -or $Message.Contains("`n") -or $Message.Contains("`r")) {
    throw "El mensaje del commit debe ser una sola línea no vacía."
}

$tracked = @(Invoke-Git -GitArgs @("ls-files"))
$untracked = @(Invoke-Git -GitArgs @("ls-files", "--others", "--exclude-standard"))
$paths = @($tracked + $untracked | Sort-Object -Unique)
if ($paths.Count -eq 0) {
    throw "No se encontraron archivos del proyecto para sincronizar."
}
$requiredFiles = @("sync-to-github.sh", ".github/workflows/android.yml", "app/build.gradle")
foreach ($requiredFile in $requiredFiles) {
    if (-not (Test-Path -LiteralPath (Join-Path $repoRoot $requiredFile) -PathType Leaf)) {
        throw "Falta un archivo requerido para publicar con seguridad: $requiredFile"
    }
}

$preparedDirectories = [System.Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach ($relativePath in $paths) {
    if ($relativePath -notmatch '^[A-Za-z0-9._/-]+$' -or $relativePath.StartsWith("/") -or $relativePath -match '(^|/)\.\.(/|$)') {
        throw "Ruta no admitida por seguridad: $relativePath"
    }

    $localPath = Join-Path $repoRoot $relativePath.Replace('/', [IO.Path]::DirectorySeparatorChar)
    $remotePath = "$remoteRoot/$relativePath"
    if (Test-Path -LiteralPath $localPath -PathType Leaf) {
        $remoteDirectory = $remotePath.Substring(0, $remotePath.LastIndexOf('/'))
        if ($preparedDirectories.Add($remoteDirectory)) {
            & ssh $serverAlias "mkdir -p -- '$remoteDirectory'"
            if ($LASTEXITCODE -ne 0) { throw "No se pudo preparar la carpeta remota de $relativePath" }
        }
        & scp -- $localPath "${serverAlias}:$remotePath"
        if ($LASTEXITCODE -ne 0) { throw "No se pudo copiar $relativePath al servidor" }
    }
    else {
        & ssh $serverAlias "rm -f -- '$remotePath'"
        if ($LASTEXITCODE -ne 0) { throw "No se pudo propagar la eliminación de $relativePath" }
    }
}

$remoteSyncScript = "$remoteRoot/sync-to-github.sh"
& ssh $serverAlias "chmod +x '$remoteSyncScript'"
if ($LASTEXITCODE -ne 0) { throw "No se pudo habilitar el script de publicación en el servidor." }

$encodedMessage = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Message))
$remoteCommand = "cd '$remoteRoot' && ./sync-to-github.sh --message-base64 '$encodedMessage' --release"
& ssh $serverAlias $remoteCommand
if ($LASTEXITCODE -ne 0) {
    throw "El servidor no pudo completar el commit, push y publicación del Release."
}

# GitHub es público: refrescar el clon local desde HTTPS deja local, servidor y origin alineados.
& git -c "safe.directory=$repoRoot" -C $repoRoot remote set-url origin "https://github.com/rikiluciano/Linka.git"
if ($LASTEXITCODE -ne 0) { throw "No se pudo configurar el remoto público de solo lectura." }
& git -c "safe.directory=$repoRoot" -C $repoRoot fetch origin main --tags
if ($LASTEXITCODE -ne 0) { throw "No se pudo actualizar el historial local desde GitHub." }
& git -c "safe.directory=$repoRoot" -C $repoRoot reset --hard origin/main
if ($LASTEXITCODE -ne 0) { throw "No se pudo alinear el clon local con main." }

Write-Host "Linka quedó sincronizado en este equipo, el servidor y GitHub. La publicación del APK se ejecuta en GitHub Actions."
