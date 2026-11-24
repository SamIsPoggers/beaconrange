<#
.SYNOPSIS
    Sequential non-dev (production) Minecraft playtest launcher for Beacon Bounding Boxes.
.DESCRIPTION
    Launches official non-dev Fabric (26.1 -> 26.1.1 -> 26.1.2 -> 26.2 -> 26.3) followed by
    official non-dev NeoForge (26.1 -> 26.1.1 -> 26.1.2 -> 26.2 -> 26.3) one by one as you
    close each game window, using the single universal JAR + Sodium + Iris + ultra-light shaders
    and sharing a single game directory (worlds, options, configs, shaderpacks).
#>
[CmdletBinding()]
param(
    [ValidateSet('all', 'fabric', 'neoforge')]
    [string]$Loader = 'all',
    [string]$StartAt = '',
    [string]$Only = '',
    [string]$Username = 'Player',
    [string]$Memory = '3G',
    [switch]$SkipBuild,
    [switch]$SetupOnly,
    [switch]$NoWorldReset,
    [switch]$SmokeTest,
    [int]$SmokeTestTimeoutSeconds = 120
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Ensure TLS 1.2+ on Windows PowerShell 5.1
try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 -bor [Net.SecurityProtocolType]::Tls13
} catch {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
}

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$PlaytestRoot = Join-Path $RepoRoot 'playtest'
$RuntimeDir = Join-Path $PlaytestRoot 'runtime'
$GameDir = Join-Path $PlaytestRoot 'game'
$VersionsDir = Join-Path $RuntimeDir 'versions'
$LibrariesDir = Join-Path $RuntimeDir 'libraries'
$InstallersDir = Join-Path $RuntimeDir 'installers'
$NativesBaseDir = Join-Path $RuntimeDir 'natives'
$LaunchArgsDir = Join-Path $RuntimeDir 'launch-args'
$ModCacheDir = Join-Path $RuntimeDir 'mod-cache'
$AssetsFallbackDir = Join-Path $RuntimeDir 'assets'
$SavesSnapshotDir = Join-Path $RuntimeDir 'saves-snapshot-26.1'
$SavesPreNeoForgeBackupDir = Join-Path $RuntimeDir 'saves-before-neoforge-26.1'

$GradlePropsPath = Join-Path $RepoRoot 'gradle.properties'
$GradleProps = @{}
foreach ($propLine in (Get-Content -Path $GradlePropsPath -Encoding UTF8)) {
    $trimmed = $propLine.Trim()
    if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }
    $eqIdx = $trimmed.IndexOf('=')
    if ($eqIdx -gt 0) {
        $propKey = $trimmed.Substring(0, $eqIdx).Trim()
        $propVal = $trimmed.Substring($eqIdx + 1).Trim()
        $GradleProps[$propKey] = $propVal
    }
}
$ModVersion = if ($GradleProps.ContainsKey('mod_version') -and $GradleProps['mod_version']) { $GradleProps['mod_version'] } else { throw "Missing mod_version in $GradlePropsPath" }
$ArchivesBaseName = if ($GradleProps.ContainsKey('archives_base_name') -and $GradleProps['archives_base_name']) { $GradleProps['archives_base_name'] } else { throw "Missing archives_base_name in $GradlePropsPath" }
$UniversalJarPath = Join-Path $RepoRoot "build\libs\${ArchivesBaseName}-${ModVersion}+26.1-26.3.jar"

$Utf8NoBom = [System.Text.UTF8Encoding]::new($false)

$DefaultShaderName = 'For the real potatoes.zip'
$DefaultShaderUrl = 'https://cdn.modrinth.com/data/7v7VBLD0/versions/NiFMD9PY/For%20the%20real%20potatoes.zip'
$AltShaderName = 'miniature-shader-2.19.zip'
$AltShaderUrl = 'https://cdn.modrinth.com/data/UaS8ROxa/versions/LWmZ94RG/miniature-shader-2.19.zip'

$Targets = @(
    [pscustomobject]@{
        Id = 'fabric-26.1'; Loader = 'fabric'; McVersion = '26.1'; Label = 'Fabric 26.1'
        FabricLoader = '0.20.1'; FabricApi = '0.145.1+26.1'; ModMenu = '18.0.2'
        NeoForgeVersion = ''; Yacl = '3.9.7+26.1-fabric'
        Sodium = 'mc26.1.1-0.8.9-fabric'; Iris = '1.10.9+26.1-fabric'
    },
    [pscustomobject]@{
        Id = 'fabric-26.1.1'; Loader = 'fabric'; McVersion = '26.1.1'; Label = 'Fabric 26.1.1'
        FabricLoader = '0.20.1'; FabricApi = '0.145.4+26.1.1'; ModMenu = '18.0.2'
        NeoForgeVersion = ''; Yacl = '3.9.7+26.1-fabric'
        Sodium = 'mc26.1.1-0.8.9-fabric'; Iris = '1.10.9+26.1-fabric'
    },
    [pscustomobject]@{
        Id = 'fabric-26.1.2'; Loader = 'fabric'; McVersion = '26.1.2'; Label = 'Fabric 26.1.2'
        FabricLoader = '0.20.1'; FabricApi = '0.155.3+26.1.2'; ModMenu = '18.0.2'
        NeoForgeVersion = ''; Yacl = '3.9.7+26.1-fabric'
        Sodium = 'mc26.1.2-0.9.2-fabric'; Iris = '1.11.4+26.1-fabric'
    },
    [pscustomobject]@{
        Id = 'fabric-26.2'; Loader = 'fabric'; McVersion = '26.2'; Label = 'Fabric 26.2'
        FabricLoader = '0.20.1'; FabricApi = '0.161.0+26.2'; ModMenu = '20.0.3'
        NeoForgeVersion = ''; Yacl = '3.9.7+26.2-fabric'
        Sodium = 'mc26.2-0.9.2-fabric'; Iris = '1.11.4+26.2-fabric'
    },
    [pscustomobject]@{
        Id = 'fabric-26.3'; Loader = 'fabric'; McVersion = '26.3'; Label = 'Fabric 26.3'
        FabricLoader = '0.20.1'; FabricApi = '0.167.0+26.3'; ModMenu = '21.0.1'
        NeoForgeVersion = ''; Yacl = '3.9.7+26.3-fabric'
        Sodium = 'mc26.3-0.9.2-fabric'; Iris = '1.11.7+26.3-fabric'
    },
    [pscustomobject]@{
        Id = 'neoforge-26.1'; Loader = 'neoforge'; McVersion = '26.1'; Label = 'NeoForge 26.1'
        FabricLoader = ''; FabricApi = ''; ModMenu = ''
        NeoForgeVersion = '26.1.0.19-beta'; Yacl = '3.9.7+26.1-neoforge'
        Sodium = 'mc26.1.1-0.8.9-neoforge'; Iris = '1.10.9+26.1-neoforge'
    },
    [pscustomobject]@{
        Id = 'neoforge-26.1.1'; Loader = 'neoforge'; McVersion = '26.1.1'; Label = 'NeoForge 26.1.1'
        FabricLoader = ''; FabricApi = ''; ModMenu = ''
        NeoForgeVersion = '26.1.1.15-beta'; Yacl = '3.9.7+26.1-neoforge'
        Sodium = 'mc26.1.1-0.8.9-neoforge'; Iris = '1.10.9+26.1-neoforge'
    },
    [pscustomobject]@{
        Id = 'neoforge-26.1.2'; Loader = 'neoforge'; McVersion = '26.1.2'; Label = 'NeoForge 26.1.2'
        FabricLoader = ''; FabricApi = ''; ModMenu = ''
        NeoForgeVersion = '26.1.2.129'; Yacl = '3.9.7+26.1-neoforge'
        Sodium = 'mc26.1.2-0.9.2-neoforge'; Iris = '1.11.4+26.1-neoforge'
    },
    [pscustomobject]@{
        Id = 'neoforge-26.2'; Loader = 'neoforge'; McVersion = '26.2'; Label = 'NeoForge 26.2'
        FabricLoader = ''; FabricApi = ''; ModMenu = ''
        NeoForgeVersion = '26.2.0.95'; Yacl = '3.9.7+26.2-neoforge'
        Sodium = 'mc26.2-0.9.2-neoforge'; Iris = '1.11.4+26.2-neoforge'
    },
    [pscustomobject]@{
        Id = 'neoforge-26.3'; Loader = 'neoforge'; McVersion = '26.3'; Label = 'NeoForge 26.3'
        FabricLoader = ''; FabricApi = ''; ModMenu = ''
        # Upstream YACL 3.9.7+26.3-neoforge rejects MC 26.3 ([26.2,26.3)); NeoForge 26.3 uses native FallbackConfigScreen.
        # Sodium 0.9.3-beta.1 fixes NeoForge 26.3.0.65+ LevelExtractorMixin while keeping Iris 1.11.7 compatibility.
        NeoForgeVersion = '26.3.0.115'; Yacl = ''
        Sodium = 'mc26.3-0.9.3-beta.1-neoforge'; Iris = '1.11.7+26.3-neoforge'
    }
)

function Get-JavaMajorVersion {
    param([string]$JavaExe)
    if (-not $JavaExe -or -not (Test-Path $JavaExe)) { return 0 }
    try {
        $jdkRoot = Split-Path (Split-Path $JavaExe -Parent) -Parent
        $releaseFile = Join-Path $jdkRoot 'release'
        if (Test-Path $releaseFile) {
            $relText = [System.IO.File]::ReadAllText($releaseFile, [System.Text.Encoding]::UTF8)
            if ($relText -match 'JAVA_VERSION="(\d+)') {
                return [int]$Matches[1]
            }
        }
        $fvi = [System.Diagnostics.FileVersionInfo]::GetVersionInfo($JavaExe)
        if ($fvi -and $fvi.FileMajorPart -ge 1) {
            return [int]$fvi.FileMajorPart
        }
    } catch {
        return 0
    }
    return 0
}

function Resolve-Java25 {
    $candidates = New-Object System.Collections.Generic.List[string]
    if ($env:JAVA_HOME) {
        [void]$candidates.Add((Join-Path $env:JAVA_HOME 'bin\java.exe'))
    }
    $knownRoots = @(
        (Join-Path $env:USERPROFILE '.gradle\jdks'),
        'E:\Gradle\jdks',
        'C:\Program Files\Eclipse Adoptium',
        'C:\Program Files\Java',
        'C:\Program Files\Microsoft'
    )
    foreach ($root in $knownRoots) {
        if (Test-Path $root) {
            Get-ChildItem -Path $root -Directory -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -match '25' } |
                Sort-Object Name -Descending |
                ForEach-Object {
                    [void]$candidates.Add((Join-Path $_.FullName 'bin\java.exe'))
                }
        }
    }
    $pathJava = Get-Command java -ErrorAction SilentlyContinue
    if ($pathJava) {
        [void]$candidates.Add($pathJava.Source)
    }

    foreach ($exe in $candidates) {
        $major = Get-JavaMajorVersion -JavaExe $exe
        if ($major -ge 25) {
            $javaHome = (Split-Path (Split-Path $exe -Parent) -Parent)
            return [pscustomobject]@{
                JavaExe = (Resolve-Path $exe).Path
                JavaHome = $javaHome
                Major = $major
            }
        }
    }
    throw "Java 25+ was not found. Set JAVA_HOME to a JDK 25 installation and retry."
}

function Ensure-Dir {
    param([string]$Path)
    if (-not (Test-Path $Path)) {
        New-Item -ItemType Directory -Force -Path $Path | Out-Null
    }
}

function Ensure-Download {
    param(
        [string]$Url,
        [string]$DestPath,
        [string]$ExpectedSha1 = ''
    )
    Ensure-Dir (Split-Path $DestPath -Parent)
    if (Test-Path $DestPath) {
        if (-not $ExpectedSha1) { return }
        $actual = (Get-FileHash -Algorithm SHA1 -Path $DestPath).Hash.ToLowerInvariant()
        if ($actual -eq $ExpectedSha1.ToLowerInvariant()) { return }
    }
    $tmpPath = "$DestPath.tmp"
    if (Test-Path $tmpPath) { Remove-Item -Force $tmpPath }
    Write-Host "  [download] $(Split-Path $DestPath -Leaf)"
    Invoke-WebRequest -UseBasicParsing -Uri $Url -OutFile $tmpPath
    if ($ExpectedSha1) {
        $actual = (Get-FileHash -Algorithm SHA1 -Path $tmpPath).Hash.ToLowerInvariant()
        if ($actual -ne $ExpectedSha1.ToLowerInvariant()) {
            Remove-Item -Force -ErrorAction SilentlyContinue $tmpPath
            throw "SHA-1 mismatch downloading $Url (expected $ExpectedSha1, got $actual)"
        }
    }
    Move-Item -Force $tmpPath $DestPath
}

function Resolve-MavenCoord {
    param(
        [string]$Coord,
        [string]$RepoUrl = 'https://repo1.maven.org/maven2/'
    )
    $ext = 'jar'
    $cleanCoord = $Coord
    if ($cleanCoord -match '^(.+)@([^@]+)$') {
        $cleanCoord = $Matches[1]
        $ext = $Matches[2]
    }
    $parts = $cleanCoord.Split(':')
    if ($parts.Length -lt 3) {
        throw "Invalid Maven coordinate: $Coord"
    }
    $group = $parts[0]
    $artifact = $parts[1]
    $version = $parts[2]
    $classifier = if ($parts.Length -ge 4 -and $parts[3]) { $parts[3] } else { '' }
    $groupPath = $group.Replace('.', '/')
    $fileName = if ($classifier) { "$artifact-$version-$classifier.$ext" } else { "$artifact-$version.$ext" }
    $relPath = "$groupPath/$artifact/$version/$fileName"
    $baseUrl = if ($RepoUrl.EndsWith('/')) { $RepoUrl } else { "$RepoUrl/" }
    $dedupKey = if ($classifier) { "${group}:${artifact}:${classifier}" } else { "${group}:${artifact}" }

    return [pscustomobject]@{
        Group = $group
        Artifact = $artifact
        Version = $version
        Classifier = $classifier
        RelativePath = $relPath
        Url = "$baseUrl$relPath"
        DedupKey = $dedupKey
    }
}

function Test-MojangRules {
    param($Rules)
    if ($null -eq $Rules -or @($Rules).Count -eq 0) {
        return $true
    }
    $allowed = $false
    foreach ($rule in @($Rules)) {
        $hasFeatures = ($null -ne $rule.PSObject.Properties['features'] -and $null -ne $rule.features)
        if ($hasFeatures) {
            return $false
        }
        $matchesOs = $true
        $hasOs = ($null -ne $rule.PSObject.Properties['os'] -and $null -ne $rule.os)
        if ($hasOs) {
            if ($null -ne $rule.os.PSObject.Properties['name'] -and $rule.os.name -and $rule.os.name -ne 'windows') {
                $matchesOs = $false
            }
            if ($null -ne $rule.os.PSObject.Properties['arch'] -and $rule.os.arch) {
                $arch = $rule.os.arch
                if ($arch -ne 'x86_64' -and $arch -ne 'amd64') {
                    $matchesOs = $false
                }
            }
            if ($null -ne $rule.os.PSObject.Properties['version'] -and $rule.os.version) {
                $osVer = [Environment]::OSVersion.Version.ToString()
                if (-not ($osVer -match $rule.os.version)) {
                    $matchesOs = $false
                }
            }
        }
        if ($matchesOs) {
            $allowed = ($rule.action -eq 'allow')
        }
    }
    return $allowed
}

function Get-MojangManifest {
    $cachePath = Join-Path $RuntimeDir 'version_manifest_v2.json'
    $needsFetch = $true
    if (Test-Path $cachePath) {
        $age = (Get-Date) - (Get-Item $cachePath).LastWriteTime
        if ($age.TotalHours -lt 6) {
            $needsFetch = $false
        }
    }
    if ($needsFetch) {
        Ensure-Download -Url 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json' -DestPath $cachePath
    }
    return (Get-Content -Raw -Encoding UTF8 $cachePath | ConvertFrom-Json)
}

function Ensure-VanillaVersion {
    param(
        [string]$McVersion,
        $MojangManifest
    )
    $verDir = Join-Path $VersionsDir $McVersion
    $verJsonPath = Join-Path $verDir "$McVersion.json"
    $verJarPath = Join-Path $verDir "$McVersion.jar"
    Ensure-Dir $verDir

    if (-not (Test-Path $verJsonPath)) {
        $entry = $MojangManifest.versions | Where-Object { $_.id -eq $McVersion } | Select-Object -First 1
        if (-not $entry) {
            throw "Minecraft version '$McVersion' not found in Mojang version manifest"
        }
        Ensure-Download -Url $entry.url -DestPath $verJsonPath -ExpectedSha1 $entry.sha1
    }

    $verJson = Get-Content -Raw -Encoding UTF8 $verJsonPath | ConvertFrom-Json
    $clientDl = $verJson.downloads.client
    Ensure-Download -Url $clientDl.url -DestPath $verJarPath -ExpectedSha1 $clientDl.sha1

    foreach ($lib in $verJson.libraries) {
        $rules = if ($null -ne $lib.PSObject.Properties['rules']) { $lib.rules } else { $null }
        if (-not (Test-MojangRules -Rules $rules)) { continue }
        if ($lib.name -match ':natives-windows-(arm64|x86)$') { continue }

        $dl = $lib.downloads
        if ($dl -and $null -ne $dl.PSObject.Properties['artifact'] -and $dl.artifact) {
            $dest = Join-Path $LibrariesDir ($dl.artifact.path.Replace('/', '\'))
            if ($dl.artifact.url) {
                $sha = if ($null -ne $dl.artifact.PSObject.Properties['sha1']) { $dl.artifact.sha1 } else { '' }
                Ensure-Download -Url $dl.artifact.url -DestPath $dest -ExpectedSha1 $sha
            }
        }
        if ($dl -and $null -ne $dl.PSObject.Properties['classifiers'] -and $dl.classifiers) {
            $winNative = $dl.classifiers.PSObject.Properties['natives-windows']
            if ($winNative -and $winNative.Value -and $winNative.Value.url) {
                $nDest = Join-Path $LibrariesDir ($winNative.Value.path.Replace('/', '\'))
                $nSha = if ($null -ne $winNative.Value.PSObject.Properties['sha1']) { $winNative.Value.sha1 } else { '' }
                Ensure-Download -Url $winNative.Value.url -DestPath $nDest -ExpectedSha1 $nSha
            }
        }
    }

    return $verJson
}

function Ensure-Assets {
    param($VanillaJson)
    $assetIndex = $VanillaJson.assetIndex
    $indexId = $assetIndex.id

    $cachedRoots = @(
        'E:\Gradle\caches\neoformruntime\assets',
        (Join-Path $env:USERPROFILE '.gradle\caches\neoformruntime\assets'),
        (Join-Path $env:APPDATA '.minecraft\assets')
    )
    foreach ($candidate in $cachedRoots) {
        $idxFile = Join-Path $candidate "indexes\$indexId.json"
        if ((Test-Path $idxFile) -and (Test-Path (Join-Path $candidate 'objects'))) {
            return [pscustomobject]@{
                Root = (Resolve-Path $candidate).Path
                IndexId = $indexId
            }
        }
    }

    Ensure-Dir (Join-Path $AssetsFallbackDir 'indexes')
    Ensure-Dir (Join-Path $AssetsFallbackDir 'objects')
    $localIdx = Join-Path $AssetsFallbackDir "indexes\$indexId.json"
    Ensure-Download -Url $assetIndex.url -DestPath $localIdx -ExpectedSha1 $assetIndex.sha1

    $idxObj = Get-Content -Raw -Encoding UTF8 $localIdx | ConvertFrom-Json
    $localSeedRoot = @('E:\Gradle\caches\fabric-loom\assets', (Join-Path $env:USERPROFILE '.gradle\caches\fabric-loom\assets')) |
        Where-Object { Test-Path (Join-Path $_ 'objects') } |
        Select-Object -First 1

    foreach ($prop in $idxObj.objects.PSObject.Properties) {
        $hash = $prop.Value.hash
        $prefix = $hash.Substring(0, 2)
        $destObj = Join-Path $AssetsFallbackDir "objects\$prefix\$hash"
        if (-not (Test-Path $destObj)) {
            $copied = $false
            if ($localSeedRoot) {
                $seedObj = Join-Path $localSeedRoot "objects\$prefix\$hash"
                if (Test-Path $seedObj) {
                    Ensure-Dir (Split-Path $destObj -Parent)
                    Copy-Item -Force $seedObj $destObj
                    $copied = $true
                }
            }
            if (-not $copied) {
                Ensure-Download -Url "https://resources.download.minecraft.net/$prefix/$hash" -DestPath $destObj -ExpectedSha1 $hash
            }
        }
    }

    return [pscustomobject]@{
        Root = $AssetsFallbackDir
        IndexId = $indexId
    }
}

function Ensure-FabricProfile {
    param($Target)
    $profileId = "fabric-loader-$($Target.FabricLoader)-$($Target.McVersion)"
    $verDir = Join-Path $VersionsDir $profileId
    $verJsonPath = Join-Path $verDir "$profileId.json"
    Ensure-Dir $verDir

    if (-not (Test-Path $verJsonPath)) {
        $url = "https://meta.fabricmc.net/v2/versions/loader/$($Target.McVersion)/$($Target.FabricLoader)/profile/json"
        Ensure-Download -Url $url -DestPath $verJsonPath
    }

    $profileJson = Get-Content -Raw -Encoding UTF8 $verJsonPath | ConvertFrom-Json
    foreach ($lib in $profileJson.libraries) {
        $repo = if ($null -ne $lib.PSObject.Properties['url'] -and $lib.url) { $lib.url } else { 'https://maven.fabricmc.net/' }
        $coord = Resolve-MavenCoord -Coord $lib.name -RepoUrl $repo
        $dest = Join-Path $LibrariesDir ($coord.RelativePath.Replace('/', '\'))
        $sha = if ($null -ne $lib.PSObject.Properties['sha1'] -and $lib.sha1) { $lib.sha1 } else { '' }
        Ensure-Download -Url $coord.Url -DestPath $dest -ExpectedSha1 $sha
    }
    return $profileJson
}

function Ensure-NeoForgeProfile {
    param(
        $Target,
        $JavaInfo
    )
    $nfVer = $Target.NeoForgeVersion
    $profileId = "neoforge-$nfVer"
    $verDir = Join-Path $VersionsDir $profileId
    $verJsonPath = Join-Path $verDir "$profileId.json"

    if (-not (Test-Path $verJsonPath)) {
        Ensure-Dir $InstallersDir
        $launcherProfiles = Join-Path $RuntimeDir 'launcher_profiles.json'
        if (-not (Test-Path $launcherProfiles)) {
            [System.IO.File]::WriteAllText($launcherProfiles, "{`n  `"profiles`": {}`n}`n", $Utf8NoBom)
        }

        $installerJar = Join-Path $InstallersDir "neoforge-$nfVer-installer.jar"
        $installerUrl = "https://maven.neoforged.net/releases/net/neoforged/neoforge/$nfVer/neoforge-$nfVer-installer.jar"
        Ensure-Download -Url $installerUrl -DestPath $installerJar

        Write-Host "  [install] Running NeoForge $nfVer client installer into $RuntimeDir ..."
        $psi = [System.Diagnostics.ProcessStartInfo]::new()
        $psi.FileName = $JavaInfo.JavaExe
        $psi.WorkingDirectory = $InstallersDir
        $psi.UseShellExecute = $false
        $psi.Arguments = (Format-WinCommandLine @('-jar', $installerJar, '--installClient', $RuntimeDir))
        $proc = [System.Diagnostics.Process]::Start($psi)
        $proc.WaitForExit()
        if ($proc.ExitCode -ne 0) {
            throw "NeoForge $nfVer installer exited with code $($proc.ExitCode)"
        }
        if (-not (Test-Path $verJsonPath)) {
            throw "NeoForge $nfVer installer finished without creating $verJsonPath"
        }
    }

    $profileJson = Get-Content -Raw -Encoding UTF8 $verJsonPath | ConvertFrom-Json
    foreach ($lib in $profileJson.libraries) {
        if ($null -ne $lib.PSObject.Properties['downloads'] -and $lib.downloads -and
            $null -ne $lib.downloads.PSObject.Properties['artifact'] -and $lib.downloads.artifact) {
            $art = $lib.downloads.artifact
            $dest = Join-Path $LibrariesDir ($art.path.Replace('/', '\'))
            if (-not (Test-Path $dest) -and $art.url) {
                $sha = if ($null -ne $art.PSObject.Properties['sha1']) { $art.sha1 } else { '' }
                Ensure-Download -Url $art.url -DestPath $dest -ExpectedSha1 $sha
            }
        }
    }
    return $profileJson
}

function Ensure-ModCache {
    param($Target)
    $cacheDir = Join-Path $ModCacheDir $Target.Id
    Ensure-Dir $cacheDir

    $mods = New-Object System.Collections.Generic.List[object]
    if ($Target.Loader -eq 'fabric') {
        $c1 = Resolve-MavenCoord -Coord "net.fabricmc.fabric-api:fabric-api:$($Target.FabricApi)" -RepoUrl 'https://maven.fabricmc.net/'
        $c2 = Resolve-MavenCoord -Coord "com.terraformersmc:modmenu:$($Target.ModMenu)" -RepoUrl 'https://maven.terraformersmc.com/releases/'
        [void]$mods.Add($c1)
        [void]$mods.Add($c2)
    }
    if ($Target.Yacl) {
        [void]$mods.Add((Resolve-MavenCoord -Coord "dev.isxander:yet-another-config-lib:$($Target.Yacl)" -RepoUrl 'https://maven.isxander.dev/releases/'))
    }
    [void]$mods.Add((Resolve-MavenCoord -Coord "maven.modrinth:sodium:$($Target.Sodium)" -RepoUrl 'https://api.modrinth.com/maven/'))
    [void]$mods.Add((Resolve-MavenCoord -Coord "maven.modrinth:iris:$($Target.Iris)" -RepoUrl 'https://api.modrinth.com/maven/'))

    foreach ($mod in $mods) {
        $fileName = Split-Path $mod.RelativePath -Leaf
        $dest = Join-Path $cacheDir $fileName
        Ensure-Download -Url $mod.Url -DestPath $dest
    }
    return $cacheDir
}

function Ensure-SharedGameConfigAndShaders {
    foreach ($sub in @('config', 'mods', 'saves', 'shaderpacks', 'resourcepacks', 'screenshots')) {
        Ensure-Dir (Join-Path $GameDir $sub)
    }

    $shaderpacksDir = Join-Path $GameDir 'shaderpacks'
    Ensure-Download -Url $DefaultShaderUrl -DestPath (Join-Path $shaderpacksDir $DefaultShaderName)
    Ensure-Download -Url $AltShaderUrl -DestPath (Join-Path $shaderpacksDir $AltShaderName)

    $altSettings = Join-Path $shaderpacksDir "$AltShaderName.txt"
    if (-not (Test-Path $altSettings)) {
        [System.IO.File]::WriteAllText($altSettings, "profile=LOWEST`n", $Utf8NoBom)
    }

    $irisProps = Join-Path $GameDir 'config\iris.properties'
    if (-not (Test-Path $irisProps)) {
        $propsContent = @(
            'enableShaders=true'
            "shaderPack=$DefaultShaderName"
            'disableUpdateMessage=false'
            'allowUnknownShaders=false'
            'colorSpace=SRGB'
            'enableDebugOptions=false'
            'maxShadowRenderDistance=16'
        ) -join "`n"
        [System.IO.File]::WriteAllText($irisProps, "$propsContent`n", $Utf8NoBom)
    } else {
        $rawIris = [System.IO.File]::ReadAllText($irisProps, $Utf8NoBom)
        if ($rawIris -match '(?m)^shaderPack=\s*$') {
            $updatedIris = [regex]::Replace($rawIris, '(?m)^shaderPack=\s*$', "shaderPack=$DefaultShaderName")
            $updatedIris = [regex]::Replace($updatedIris, '(?m)^enableShaders=false\s*$', 'enableShaders=true')
            [System.IO.File]::WriteAllText($irisProps, $updatedIris, $Utf8NoBom)
        }
    }

    # Prevent OpenGL 3.3 no-error context native crash on old NVIDIA drivers (e.g. Quadro 4000 / 377.83)
    $sodiumOpts = Join-Path $GameDir 'config\sodium-options.json'
    if (-not (Test-Path $sodiumOpts)) {
        $minJson = "{\n  `"performance`": {\n    `"use_no_error_g_l_context`": false\n  }\n}\n".Replace('\n', "`n")
        [System.IO.File]::WriteAllText($sodiumOpts, $minJson, $Utf8NoBom)
    } else {
        $rawSodium = [System.IO.File]::ReadAllText($sodiumOpts, $Utf8NoBom)
        $updatedSodium = [regex]::Replace($rawSodium, '"use_no_error_g_l_context"\s*:\s*true', '"use_no_error_g_l_context": false')
        if ($updatedSodium -ne $rawSodium) {
            [System.IO.File]::WriteAllText($sodiumOpts, $updatedSodium, $Utf8NoBom)
        }
    }
}

function Read-TextShared {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return '' }
    try {
        $fs = [System.IO.FileStream]::new($Path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
        try {
            $sr = [System.IO.StreamReader]::new($fs, [System.Text.Encoding]::UTF8, $true)
            try {
                return $sr.ReadToEnd()
            } finally {
                $sr.Dispose()
            }
        } finally {
            $fs.Dispose()
        }
    } catch {
        return ''
    }
}

function Write-JavaArgFile {
    param(
        [string]$TargetId,
        [string[]]$ArgList
    )
    Ensure-Dir $LaunchArgsDir
    $argFile = Join-Path $LaunchArgsDir "$TargetId.argfile"
    $lines = foreach ($arg in $ArgList) {
        if ($null -eq $arg -or $arg.Length -eq 0) {
            '""'
            continue
        }
        $escaped = $arg.Replace('\', '\\').Replace('"', '\"')
        "`"$escaped`""
    }
    [System.IO.File]::WriteAllText($argFile, (($lines -join "`n") + "`n"), $Utf8NoBom)
    return $argFile
}

function Format-WinCommandLine {
    param([string[]]$ArgList)
    $quoted = foreach ($arg in $ArgList) {
        if ($null -eq $arg) {
            '""'
            continue
        }
        if ($arg.Length -eq 0 -or $arg -match '[\s"]') {
            $escaped = [regex]::Replace($arg, '(\\*)"', '$1$1\"')
            $escaped = [regex]::Replace($escaped, '(\\+)$', '$1$1')
            "`"$escaped`""
        } else {
            $arg
        }
    }
    return ($quoted -join ' ')
}

function Convert-MojangArgList {
    param($Items)
    $out = New-Object System.Collections.Generic.List[string]
    if ($null -eq $Items) { return , @() }
    foreach ($item in @($Items)) {
        if ($item -is [string]) {
            [void]$out.Add($item)
        } elseif ($null -ne $item.PSObject.Properties['rules'] -and $null -ne $item.PSObject.Properties['value']) {
            if (Test-MojangRules -Rules $item.rules) {
                if ($item.value -is [System.Array]) {
                    foreach ($v in $item.value) { [void]$out.Add([string]$v) }
                } else {
                    [void]$out.Add([string]$item.value)
                }
            }
        }
    }
    return , $out.ToArray()
}

function Get-OfflinePlayerUuid {
    param([string]$PlayerName)
    $md5 = [System.Security.Cryptography.MD5]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes("OfflinePlayer:$PlayerName")
        $hash = $md5.ComputeHash($bytes)
        $hash[6] = ($hash[6] -band 0x0F) -bor 0x30
        $hash[8] = ($hash[8] -band 0x3F) -bor 0x80
        return ([BitConverter]::ToString($hash).Replace('-', '').ToLowerInvariant())
    } finally {
        $md5.Dispose()
    }
}

function Replace-Placeholders {
    param(
        [string]$Text,
        [hashtable]$Vars
    )
    $result = $Text
    foreach ($key in $Vars.Keys) {
        $result = $result.Replace('${' + $key + '}', $Vars[$key])
    }
    return $result
}

function Expand-NativeJar {
    param(
        [string]$JarPath,
        [string]$NativesDir
    )
    if (-not (Test-Path $JarPath)) { return }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        foreach ($entry in $zip.Entries) {
            if (-not $entry.Name) { continue }
            if ($entry.FullName -like 'META-INF/*' -or $entry.FullName -like '*.git*' -or $entry.FullName -like '*.sha1') { continue }
            $dest = Join-Path $NativesDir $entry.Name
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $dest, $true)
        }
    } finally {
        $zip.Dispose()
    }
}

function Build-LaunchSpec {
    param(
        $Target,
        $VanillaJson,
        $LoaderJson,
        $AssetsInfo
    )
    $libOrderedKeys = New-Object System.Collections.Generic.List[string]
    $libMap = @{}
    $legacyNatives = New-Object System.Collections.Generic.List[string]

    # Child (loader) libraries take priority on version conflicts over vanilla libraries
    foreach ($lib in $LoaderJson.libraries) {
        $repo = if ($null -ne $lib.PSObject.Properties['url'] -and $lib.url) { $lib.url } else { '' }
        $rel = ''
        $key = ''
        if ($null -ne $lib.PSObject.Properties['downloads'] -and $lib.downloads -and
            $null -ne $lib.downloads.PSObject.Properties['artifact'] -and $lib.downloads.artifact) {
            $rel = $lib.downloads.artifact.path
            $key = (Resolve-MavenCoord -Coord $lib.name).DedupKey
        } else {
            $coord = Resolve-MavenCoord -Coord $lib.name -RepoUrl $repo
            $rel = $coord.RelativePath
            $key = $coord.DedupKey
        }
        $fullPath = Join-Path $LibrariesDir ($rel.Replace('/', '\'))
        if (-not (Test-Path $fullPath)) {
            throw "Missing loader library for $($Target.Id): $fullPath"
        }
        if (-not $libMap.ContainsKey($key)) {
            [void]$libOrderedKeys.Add($key)
        }
        $libMap[$key] = $fullPath
    }

    foreach ($lib in $VanillaJson.libraries) {
        $rules = if ($null -ne $lib.PSObject.Properties['rules']) { $lib.rules } else { $null }
        if (-not (Test-MojangRules -Rules $rules)) { continue }
        if ($lib.name -match ':natives-windows-(arm64|x86)$') { continue }

        $dl = $lib.downloads
        if ($dl -and $null -ne $dl.PSObject.Properties['artifact'] -and $dl.artifact) {
            $coord = Resolve-MavenCoord -Coord $lib.name
            $fullPath = Join-Path $LibrariesDir ($dl.artifact.path.Replace('/', '\'))
            if (-not $libMap.ContainsKey($coord.DedupKey)) {
                [void]$libOrderedKeys.Add($coord.DedupKey)
                $libMap[$coord.DedupKey] = $fullPath
            }
        }
        if ($dl -and $null -ne $dl.PSObject.Properties['classifiers'] -and $dl.classifiers) {
            $winNative = $dl.classifiers.PSObject.Properties['natives-windows']
            if ($winNative -and $winNative.Value) {
                $nPath = Join-Path $LibrariesDir ($winNative.Value.path.Replace('/', '\'))
                [void]$legacyNatives.Add($nPath)
            }
        }
    }

    $cpEntries = New-Object System.Collections.Generic.List[string]
    foreach ($k in $libOrderedKeys) {
        [void]$cpEntries.Add($libMap[$k])
    }

    # Mojang Launcher puts client jar on classpath for Fabric (which sets inheritsFrom=<mcVer>).
    # On NeoForge, FML's GameLocator loads NeoForge + patched Minecraft jars from libraryDirectory.
    $clientJar = Join-Path $VersionsDir "$($Target.McVersion)\$($Target.McVersion).jar"
    if ($Target.Loader -eq 'fabric') {
        [void]$cpEntries.Add($clientJar)
    }

    $nativesDir = Join-Path $NativesBaseDir $Target.Id
    foreach ($sub in @('', 'java', 'jna', 'lwjgl', 'netty')) {
        $d = if ($sub) { Join-Path $nativesDir $sub } else { $nativesDir }
        Ensure-Dir $d
    }
    foreach ($nJar in $legacyNatives) {
        Expand-NativeJar -JarPath $nJar -NativesDir $nativesDir
    }

    $classpath = ($cpEntries -join ';')
    $versionId = [string]$LoaderJson.id
    $vars = @{
        natives_directory = $nativesDir
        launcher_name = 'minecraft-launcher'
        launcher_version = '2.0'
        classpath = $classpath
        library_directory = $LibrariesDir
        classpath_separator = ';'
        auth_player_name = $Username
        version_name = $versionId
        game_directory = $GameDir
        assets_root = $AssetsInfo.Root
        assets_index_name = $AssetsInfo.IndexId
        auth_uuid = (Get-OfflinePlayerUuid -PlayerName $Username)
        auth_access_token = '0'
        clientid = '0'
        auth_xuid = '0'
        user_type = 'msa'
        version_type = 'release'
    }

    $jvmArgs = New-Object System.Collections.Generic.List[string]
    [void]$jvmArgs.Add("-Xmx$Memory")

    $rawVanillaJvm = @(Convert-MojangArgList -Items $VanillaJson.arguments.jvm)
    $rawLoaderJvm = if ($null -ne $LoaderJson.PSObject.Properties['arguments'] -and $LoaderJson.arguments -and
                        $null -ne $LoaderJson.arguments.PSObject.Properties['jvm']) {
        @(Convert-MojangArgList -Items $LoaderJson.arguments.jvm)
    } else {
        @()
    }

    $sawCp = $false
    foreach ($rawList in @($rawVanillaJvm, $rawLoaderJvm)) {
        for ($i = 0; $i -lt $rawList.Count; $i++) {
            $arg = $rawList[$i]
            if ($arg -eq '-cp' -or $arg -eq '-classpath') {
                if (($i + 1) -lt $rawList.Count) {
                    $nextVal = Replace-Placeholders -Text $rawList[$i + 1] -Vars $vars
                    if (-not $sawCp) {
                        [void]$jvmArgs.Add('-cp')
                        [void]$jvmArgs.Add($nextVal)
                        $sawCp = $true
                    }
                    $i++
                    continue
                }
            }
            [void]$jvmArgs.Add((Replace-Placeholders -Text $arg -Vars $vars))
        }
    }
    if (-not $sawCp) {
        [void]$jvmArgs.Add('-cp')
        [void]$jvmArgs.Add($classpath)
    }

    $gameArgs = New-Object System.Collections.Generic.List[string]
    foreach ($arg in (Convert-MojangArgList -Items $VanillaJson.arguments.game)) {
        [void]$gameArgs.Add((Replace-Placeholders -Text $arg -Vars $vars))
    }
    if ($null -ne $LoaderJson.PSObject.Properties['arguments'] -and $LoaderJson.arguments -and
        $null -ne $LoaderJson.arguments.PSObject.Properties['game']) {
        foreach ($arg in (Convert-MojangArgList -Items $LoaderJson.arguments.game)) {
            [void]$gameArgs.Add((Replace-Placeholders -Text $arg -Vars $vars))
        }
    }

    $allArgs = New-Object System.Collections.Generic.List[string]
    foreach ($a in $jvmArgs) { [void]$allArgs.Add($a) }
    [void]$allArgs.Add([string]$LoaderJson.mainClass)
    foreach ($a in $gameArgs) { [void]$allArgs.Add($a) }

    return [pscustomobject]@{
        Arguments = $allArgs.ToArray()
        NativesDir = $nativesDir
    }
}

function Sync-ModsForTarget {
    param(
        [string]$ModCacheForTarget
    )
    $modsDir = Join-Path $GameDir 'mods'
    Ensure-Dir $modsDir
    Get-ChildItem -Path $modsDir -Force -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force
    Copy-Item -Force $UniversalJarPath (Join-Path $modsDir (Split-Path $UniversalJarPath -Leaf))
    Get-ChildItem -Path $ModCacheForTarget -Filter '*.jar' -File | ForEach-Object {
        Copy-Item -Force $_.FullName (Join-Path $modsDir $_.Name)
    }
}

function Get-McVersionOrder {
    param([string]$Ver)
    $parts = $Ver.Split('.')
    $major = [int]$parts[0]
    $minor = if ($parts.Length -ge 2) { [int]$parts[1] } else { 0 }
    $patch = if ($parts.Length -ge 3) { [int]$parts[2] } else { 0 }
    return ($major * 10000 + $minor * 100 + $patch)
}

function Save-261WorldSnapshot {
    $savesDir = Join-Path $GameDir 'saves'
    $worlds = @(Get-ChildItem -Path $savesDir -Directory -ErrorAction SilentlyContinue)
    if ($worlds.Count -gt 0) {
        if (Test-Path $SavesSnapshotDir) { Remove-Item -Recurse -Force $SavesSnapshotDir }
        Ensure-Dir $SavesSnapshotDir
        Copy-Item -Recurse -Force (Join-Path $savesDir '*') $SavesSnapshotDir
        Write-Host "  [saves] Snapshotted $($worlds.Count) MC 26.1 world(s) -> $SavesSnapshotDir"
    }
}

function Restore-261WorldSnapshotIfNeeded {
    $savesDir = Join-Path $GameDir 'saves'
    if (Test-Path $SavesSnapshotDir) {
        $existingWorlds = @(Get-ChildItem -Path $savesDir -Directory -ErrorAction SilentlyContinue)
        if ($existingWorlds.Count -gt 0) {
            if (Test-Path $SavesPreNeoForgeBackupDir) { Remove-Item -Recurse -Force $SavesPreNeoForgeBackupDir }
            Ensure-Dir $SavesPreNeoForgeBackupDir
            Copy-Item -Recurse -Force (Join-Path $savesDir '*') $SavesPreNeoForgeBackupDir
            Remove-Item -Recurse -Force (Join-Path $savesDir '*')
        }
        Copy-Item -Recurse -Force (Join-Path $SavesSnapshotDir '*') $savesDir
        Write-Host "  [saves] Restored MC 26.1 world snapshot for NeoForge 26.1 (pre-reset 26.3 saves backed up to $SavesPreNeoForgeBackupDir)."
    }
}

function Invoke-SmokeLaunch {
    param(
        $Target,
        [string]$JavaExe,
        [string]$ArgFilePath,
        [int]$TimeoutSeconds
    )
    $latestLog = Join-Path $GameDir 'logs\latest.log'
    if (Test-Path $latestLog) {
        Remove-Item -Force $latestLog
    }

    $psi = [System.Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $JavaExe
    $psi.WorkingDirectory = $GameDir
    $psi.UseShellExecute = $false
    $psi.Arguments = (Format-WinCommandLine @("@$ArgFilePath"))

    $proc = [System.Diagnostics.Process]::Start($psi)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $ready = $false
    $lastErr = ''

    try {
        while ((Get-Date) -lt $deadline) {
            Start-Sleep -Milliseconds 500
            $logText = Read-TextShared -Path $latestLog

            if ($logText -match '(?m)(FATAL|MixinApplyError|Incompatible mods|Shader compilation failed|Failed to compile shader)') {
                $lastErr = "Detected fatal/shader error in latest.log"
                break
            }

            if ($proc.HasExited) {
                if ($proc.ExitCode -ne 0) {
                    $lastErr = "Client exited prematurely with code $($proc.ExitCode)"
                } else {
                    $lastErr = "Client exited prematurely with code 0 before reaching title screen"
                }
                break
            }

            $soundReady = ($logText -match 'Sound engine started')
            $atlasReady = ($logText -match 'blocks\.png-atlas')
            if ($soundReady -and $atlasReady) {
                $ready = $true
                break
            }
        }

        if (-not $ready) {
            if (-not $lastErr) { $lastErr = "Timed out after ${TimeoutSeconds}s waiting for title screen initialization" }
            throw "$($Target.Id) production smoke test failed: $lastErr"
        }

        # Let title screen + Iris pipeline tick cleanly before closing
        Start-Sleep -Seconds 2
        $finalLog = Read-TextShared -Path $latestLog

        if ($finalLog -match 'bbb_self_test') {
            throw "$($Target.Id) smoke test unexpectedly loaded bbb_self_test!"
        }
        if ($Target.Loader -eq 'neoforge' -and ($finalLog -notmatch 'CLIENT in PROD')) {
            throw "$($Target.Id) did not log 'CLIENT in PROD'"
        }
        if ($Target.Loader -eq 'fabric' -and ($finalLog -match 'Development environment detected')) {
            throw "$($Target.Id) unexpectedly launched in Fabric development mode"
        }
        if ($finalLog -notmatch 'beacon_bounding_boxes') {
            throw "$($Target.Id) did not load beacon_bounding_boxes"
        }
        if ($finalLog -match '(?im)(shader.*(error|failed)|GLSL.*error)') {
            throw "$($Target.Id) reported shader compilation issues in latest.log"
        }

        Write-Host "  [smoke] PASS: $($Target.Id) reached title screen in non-dev mode cleanly."
    } finally {
        if (-not $proc.HasExited) {
            try { [void]$proc.CloseMainWindow() } catch {}
            if (-not $proc.WaitForExit(3000)) {
                try { $proc.Kill() } catch {}
                $proc.WaitForExit()
            }
        }
    }
}

# === Main Execution ===
$javaInfo = Resolve-Java25
Write-Host "Using Java $($javaInfo.Major): $($javaInfo.JavaExe)"

if (-not $SkipBuild) {
    Write-Host "Building universal JAR via Gradle..."
    $prevJavaHome = $env:JAVA_HOME
    $env:JAVA_HOME = $javaInfo.JavaHome
    try {
        Push-Location $RepoRoot
        & (Join-Path $RepoRoot 'gradlew.bat') 'buildUniversalJar' '--no-daemon'
        if ($LASTEXITCODE -ne 0) {
            throw "buildUniversalJar failed with exit code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
        $env:JAVA_HOME = $prevJavaHome
    }
}

if (-not (Test-Path $UniversalJarPath)) {
    throw "Universal JAR missing: $UniversalJarPath (run without -SkipBuild first)"
}

$selected = @($Targets | Where-Object {
    ($Loader -eq 'all') -or ($_.Loader -eq $Loader)
})
if ($Only) {
    $selected = @($selected | Where-Object { $_.Id -eq $Only })
    if ($selected.Count -eq 0) {
        $valid = ($Targets | ForEach-Object { $_.Id }) -join ', '
        throw "Unknown -Only target '$Only'. Valid targets: $valid"
    }
} elseif ($StartAt) {
    $idx = -1
    for ($i = 0; $i -lt $selected.Count; $i++) {
        if ($selected[$i].Id -eq $StartAt) { $idx = $i; break }
    }
    if ($idx -lt 0) {
        $valid = ($selected | ForEach-Object { $_.Id }) -join ', '
        throw "Unknown -StartAt target '$StartAt'. Valid targets: $valid"
    }
    $selected = @($selected[$idx..($selected.Count - 1)])
}

Ensure-Dir $PlaytestRoot
Ensure-Dir $RuntimeDir
Ensure-SharedGameConfigAndShaders

Write-Host "Preparing production runtimes & mods for $($selected.Count) target(s)..."
$mojangManifest = Get-MojangManifest
$vanillaCache = @{}
$assetsCache = @{}
$prepared = New-Object System.Collections.Generic.List[object]

foreach ($t in $selected) {
    Write-Host "[prep] $($t.Id) (MC $($t.McVersion))..."
    if (-not $vanillaCache.ContainsKey($t.McVersion)) {
        $vJson = Ensure-VanillaVersion -McVersion $t.McVersion -MojangManifest $mojangManifest
        $vanillaCache[$t.McVersion] = $vJson
        $assetsCache[$t.McVersion] = Ensure-Assets -VanillaJson $vJson
    }
    $loaderJson = if ($t.Loader -eq 'fabric') {
        Ensure-FabricProfile -Target $t
    } else {
        Ensure-NeoForgeProfile -Target $t -JavaInfo $javaInfo
    }
    $modCacheDir = Ensure-ModCache -Target $t
    $launchSpec = Build-LaunchSpec -Target $t -VanillaJson $vanillaCache[$t.McVersion] -LoaderJson $loaderJson -AssetsInfo $assetsCache[$t.McVersion]
    [void]$prepared.Add([pscustomobject]@{
        Target = $t
        ModCacheDir = $modCacheDir
        LaunchSpec = $launchSpec
    })
}

Write-Host ""
Write-Host "Shared game folder : $GameDir"
Write-Host "Universal mod JAR  : $UniversalJarPath"
Write-Host "Default shader     : $DefaultShaderName (alt: $AltShaderName on LOWEST profile)"

if ($SetupOnly) {
    Write-Host "Setup complete (-SetupOnly). Ready to playtest all $($prepared.Count) target(s)."
    exit 0
}

$highestMcOrderSeen = 0
for ($i = 0; $i -lt $prepared.Count; $i++) {
    $item = $prepared[$i]
    $t = $item.Target

    $curOrder = Get-McVersionOrder -Ver $t.McVersion
    if (-not $NoWorldReset -and $t.Id -eq 'neoforge-26.1' -and $highestMcOrderSeen -gt $curOrder) {
        Restore-261WorldSnapshotIfNeeded
    }
    if ($curOrder -gt $highestMcOrderSeen) {
        $highestMcOrderSeen = $curOrder
    }

    Sync-ModsForTarget -ModCacheForTarget $item.ModCacheDir

    Write-Host ""
    Write-Host "========================================================================"
    Write-Host ("[{0}/{1}] Launching {2} (Non-Dev Production)..." -f ($i + 1), $prepared.Count, $t.Label)
    Write-Host "========================================================================"

    $argFilePath = Write-JavaArgFile -TargetId $t.Id -ArgList $item.LaunchSpec.Arguments

    if ($SmokeTest) {
        Invoke-SmokeLaunch -Target $t -JavaExe $javaInfo.JavaExe -ArgFilePath $argFilePath -TimeoutSeconds $SmokeTestTimeoutSeconds
        continue
    }

    $autoDriverRetried = $false
    while ($true) {
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        $proc = Start-Process -FilePath $javaInfo.JavaExe -ArgumentList "`"@$argFilePath`"" -WorkingDirectory $GameDir -WindowStyle Normal -Wait -PassThru
        $sw.Stop()
        $exitCode = if ($null -ne $proc -and $null -ne $proc.ExitCode) { $proc.ExitCode } else { 0 }

        if ($exitCode -eq 0) {
            if (-not $NoWorldReset -and $t.Id -eq 'fabric-26.1') {
                Save-261WorldSnapshot
            }
            break
        }

        if ($exitCode -eq -1073741819 -and -not $autoDriverRetried -and $sw.Elapsed.TotalSeconds -lt 25) {
            $autoDriverRetried = $true
            Write-Warning "$($t.Label) hit transient native OpenGL driver crash (0xC0000005) on startup; retrying once..."
            continue
        }

        Write-Warning "$($t.Label) exited with code $exitCode."
        $choice = Read-Host "Press [R]etry, [N]ext version (default), or [Q]uit"
        if ($choice -match '^[Rr]') {
            continue
        } elseif ($choice -match '^[Qq]') {
            Write-Host "Playtest stopped by user before next version."
            exit $exitCode
        } else {
            break
        }
    }
}

Write-Host ""
Write-Host "All requested playtest launches completed!"
