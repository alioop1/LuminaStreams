# LuminaStreams Setup - Windows Installer
# Compiled to EXE via ps2exe

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

[System.Windows.Forms.Application]::EnableVisualStyles()

# ==============================================================================
# GLOBALS
# ==============================================================================

$script:ProjectRoot = ""
$script:AndroidSdkPath = "$env:LOCALAPPDATA\Android\Sdk"
$script:JdkPath = "C:\Program Files\Java\jdk-25.0.2"
$script:LogLines = [System.Collections.ArrayList]::new()
$script:CurrentStep = 0
$script:TotalSteps = 8
$script:Errors = 0
$script:Warnings = 0

# ==============================================================================
# CREATE MAIN FORM
# ==============================================================================

$form = New-Object System.Windows.Forms.Form
$form.Text = "LuminaStreams Setup"
$form.Size = New-Object System.Drawing.Size(720, 580)
$form.StartPosition = "CenterScreen"
$form.FormBorderStyle = "FixedSingle"
$form.MaximizeBox = $false
$form.BackColor = [System.Drawing.Color]::FromArgb(18, 18, 24)
$form.ForeColor = [System.Drawing.Color]::White
$form.Font = New-Object System.Drawing.Font("Segoe UI", 9)

# --- Header Panel ---
$headerPanel = New-Object System.Windows.Forms.Panel
$headerPanel.Dock = "Top"
$headerPanel.Height = 80
$headerPanel.BackColor = [System.Drawing.Color]::FromArgb(30, 30, 45)
$form.Controls.Add($headerPanel)

$titleLabel = New-Object System.Windows.Forms.Label
$titleLabel.Text = "LUMINA STREAMS"
$titleLabel.Font = New-Object System.Drawing.Font("Segoe UI", 22, [System.Drawing.FontStyle]::Bold)
$titleLabel.ForeColor = [System.Drawing.Color]::FromArgb(130, 100, 255)
$titleLabel.Location = New-Object System.Drawing.Point(24, 10)
$titleLabel.AutoSize = $true
$headerPanel.Controls.Add($titleLabel)

$subtitleLabel = New-Object System.Windows.Forms.Label
$subtitleLabel.Text = "Windows Development Environment Setup v1.0"
$subtitleLabel.Font = New-Object System.Drawing.Font("Segoe UI", 9)
$subtitleLabel.ForeColor = [System.Drawing.Color]::FromArgb(140, 140, 160)
$subtitleLabel.Location = New-Object System.Drawing.Point(26, 52)
$subtitleLabel.AutoSize = $true
$headerPanel.Controls.Add($subtitleLabel)

# --- Progress Bar ---
$progressBar = New-Object System.Windows.Forms.ProgressBar
$progressBar.Location = New-Object System.Drawing.Point(24, 92)
$progressBar.Size = New-Object System.Drawing.Size(656, 8)
$progressBar.Style = "Continuous"
$progressBar.Minimum = 0
$progressBar.Maximum = 100
$progressBar.Value = 0
$form.Controls.Add($progressBar)

# --- Step Label ---
$stepLabel = New-Object System.Windows.Forms.Label
$stepLabel.Text = "Ready to install..."
$stepLabel.Font = New-Object System.Drawing.Font("Segoe UI Semibold", 10)
$stepLabel.ForeColor = [System.Drawing.Color]::FromArgb(200, 180, 255)
$stepLabel.Location = New-Object System.Drawing.Point(24, 108)
$stepLabel.Size = New-Object System.Drawing.Size(656, 22)
$form.Controls.Add($stepLabel)

# --- Log TextBox ---
$logBox = New-Object System.Windows.Forms.RichTextBox
$logBox.Location = New-Object System.Drawing.Point(24, 136)
$logBox.Size = New-Object System.Drawing.Size(656, 330)
$logBox.ReadOnly = $true
$logBox.BackColor = [System.Drawing.Color]::FromArgb(12, 12, 18)
$logBox.ForeColor = [System.Drawing.Color]::FromArgb(200, 200, 210)
$logBox.Font = New-Object System.Drawing.Font("Cascadia Code,Consolas", 9)
$logBox.BorderStyle = "None"
$logBox.ScrollBars = "Vertical"
$form.Controls.Add($logBox)

# --- Buttons Panel ---
$buttonPanel = New-Object System.Windows.Forms.Panel
$buttonPanel.Location = New-Object System.Drawing.Point(0, 480)
$buttonPanel.Size = New-Object System.Drawing.Size(720, 70)
$buttonPanel.BackColor = [System.Drawing.Color]::FromArgb(22, 22, 32)
$form.Controls.Add($buttonPanel)

# --- Browse Button ---
$browseBtn = New-Object System.Windows.Forms.Button
$browseBtn.Text = "Browse Project..."
$browseBtn.Size = New-Object System.Drawing.Size(140, 38)
$browseBtn.Location = New-Object System.Drawing.Point(24, 14)
$browseBtn.FlatStyle = "Flat"
$browseBtn.BackColor = [System.Drawing.Color]::FromArgb(45, 45, 65)
$browseBtn.ForeColor = [System.Drawing.Color]::White
$browseBtn.FlatAppearance.BorderColor = [System.Drawing.Color]::FromArgb(80, 80, 110)
$browseBtn.Cursor = "Hand"
$buttonPanel.Controls.Add($browseBtn)

# --- Install Button ---
$installBtn = New-Object System.Windows.Forms.Button
$installBtn.Text = "Install"
$installBtn.Size = New-Object System.Drawing.Size(160, 38)
$installBtn.Location = New-Object System.Drawing.Point(380, 14)
$installBtn.FlatStyle = "Flat"
$installBtn.BackColor = [System.Drawing.Color]::FromArgb(90, 60, 200)
$installBtn.ForeColor = [System.Drawing.Color]::White
$installBtn.FlatAppearance.BorderSize = 0
$installBtn.Font = New-Object System.Drawing.Font("Segoe UI Semibold", 10)
$installBtn.Cursor = "Hand"
$buttonPanel.Controls.Add($installBtn)

# --- Close Button ---
$closeBtn = New-Object System.Windows.Forms.Button
$closeBtn.Text = "Close"
$closeBtn.Size = New-Object System.Drawing.Size(120, 38)
$closeBtn.Location = New-Object System.Drawing.Point(556, 14)
$closeBtn.FlatStyle = "Flat"
$closeBtn.BackColor = [System.Drawing.Color]::FromArgb(45, 45, 65)
$closeBtn.ForeColor = [System.Drawing.Color]::White
$closeBtn.FlatAppearance.BorderColor = [System.Drawing.Color]::FromArgb(80, 80, 110)
$closeBtn.Cursor = "Hand"
$buttonPanel.Controls.Add($closeBtn)

# --- Path Label ---
$pathLabel = New-Object System.Windows.Forms.Label
$pathLabel.Text = "Project: (auto-detect)"
$pathLabel.Font = New-Object System.Drawing.Font("Segoe UI", 8)
$pathLabel.ForeColor = [System.Drawing.Color]::FromArgb(100, 100, 120)
$pathLabel.Location = New-Object System.Drawing.Point(170, 24)
$pathLabel.Size = New-Object System.Drawing.Size(200, 18)
$buttonPanel.Controls.Add($pathLabel)

# ==============================================================================
# LOGGING FUNCTIONS
# ==============================================================================

function Log {
    param([string]$Message, [string]$Type = "INFO")
    
    $color = switch ($Type) {
        "OK"     { [System.Drawing.Color]::FromArgb(80, 220, 120) }
        "WARN"   { [System.Drawing.Color]::FromArgb(255, 200, 60) }
        "ERROR"  { [System.Drawing.Color]::FromArgb(255, 80, 80) }
        "INFO"   { [System.Drawing.Color]::FromArgb(100, 160, 255) }
        "ACTION" { [System.Drawing.Color]::FromArgb(180, 140, 255) }
        "HEAD"   { [System.Drawing.Color]::FromArgb(130, 100, 255) }
        default  { [System.Drawing.Color]::FromArgb(200, 200, 210) }
    }
    
    $icon = switch ($Type) {
        "OK"     { " [OK]  " }
        "WARN"   { " [!!]  " }
        "ERROR"  { " [XX]  " }
        "INFO"   { " [--]  " }
        "ACTION" { " [>>]  " }
        "HEAD"   { "`n --- " }
        default  { "       " }
    }
    
    $logBox.SelectionStart = $logBox.TextLength
    $logBox.SelectionColor = $color
    $logBox.AppendText("$icon$Message`n")
    $logBox.ScrollToCaret()
    [System.Windows.Forms.Application]::DoEvents()
}

function Update-Progress {
    param([string]$StepName, [int]$StepNum)
    $script:CurrentStep = $StepNum
    $pct = [math]::Min(100, [int](($StepNum / $script:TotalSteps) * 100))
    $progressBar.Value = $pct
    $stepLabel.Text = "Step $StepNum/$script:TotalSteps - $StepName"
    [System.Windows.Forms.Application]::DoEvents()
}

# ==============================================================================
# AUTO-DETECT PROJECT PATH
# ==============================================================================

function Find-ProjectRoot {
    # Build candidate list, handling empty $PSScriptRoot (when running as compiled EXE)
    $candidates = [System.Collections.ArrayList]::new()
    
    # Try to get the EXE's own directory (works when compiled via ps2exe)
    try {
        $exePath = [System.Diagnostics.Process]::GetCurrentProcess().MainModule.FileName
        if ($exePath) {
            $exeDir = [System.IO.Path]::GetDirectoryName($exePath)
            if ($exeDir) {
                [void]$candidates.Add($exeDir)
                $parent = [System.IO.Path]::GetDirectoryName($exeDir)
                if ($parent) { [void]$candidates.Add($parent) }
            }
        }
    } catch {}
    
    # Try $PSScriptRoot if it's not empty
    if ($PSScriptRoot -and $PSScriptRoot.Length -gt 0) {
        [void]$candidates.Add($PSScriptRoot)
        try {
            $psParent = [System.IO.Path]::GetDirectoryName($PSScriptRoot)
            if ($psParent) { [void]$candidates.Add($psParent) }
        } catch {}
    }
    
    # Hardcoded known location
    [void]$candidates.Add("C:\Users\TechPC\Documents\LuminaStreams")
    
    # Current working directory
    try {
        $cwd = (Get-Location).Path
        if ($cwd) { [void]$candidates.Add($cwd) }
    } catch {}
    
    foreach ($c in $candidates) {
        if (-not $c -or $c.Length -eq 0) { continue }
        try {
            $resolved = [System.IO.Path]::GetFullPath($c)
            $gradlePath = Join-Path $resolved "gradlew.bat"
            $buildPath = Join-Path $resolved "app\build.gradle.kts"
            if ((Test-Path $gradlePath) -and (Test-Path $buildPath)) {
                return $resolved
            }
        } catch { continue }
    }
    return ""
}

$detectedRoot = Find-ProjectRoot
if ($detectedRoot) {
    $script:ProjectRoot = $detectedRoot
    $pathLabel.Text = "Project: $detectedRoot"
    $pathLabel.ForeColor = [System.Drawing.Color]::FromArgb(80, 220, 120)
}

# ==============================================================================
# INSTALLER LOGIC
# ==============================================================================

function Run-Installation {
    $installBtn.Enabled = $false
    $browseBtn.Enabled = $false
    $installBtn.Text = "Installing..."
    $installBtn.BackColor = [System.Drawing.Color]::FromArgb(60, 40, 130)
    $logBox.Clear()
    $script:Errors = 0
    $script:Warnings = 0
    
    $root = $script:ProjectRoot
    
    if (-not $root -or -not (Test-Path (Join-Path $root "gradlew.bat"))) {
        Log "Project directory not found! Click 'Browse Project...' to select." "ERROR"
        $installBtn.Enabled = $true
        $browseBtn.Enabled = $true
        $installBtn.Text = "Install"
        $installBtn.BackColor = [System.Drawing.Color]::FromArgb(90, 60, 200)
        return
    }
    
    Log "LuminaStreams Setup starting..." "HEAD"
    Log "Project: $root" "INFO"
    Log ""
    
    # ====== STEP 1: JAVA ======
    Update-Progress "Java Development Kit" 1
    Log "JAVA / JDK VALIDATION" "HEAD"
    
    try {
        $javaOut = & java -version 2>&1 | Out-String
        if ($javaOut -match '(\d+)\.(\d+)' -or $javaOut -match 'version "(\d+)') {
            $ver = $Matches[1]
            Log "Java detected: version $ver" "OK"
        } else {
            Log "Java detected but version unknown" "WARN"
            $script:Warnings++
        }
    } catch {
        Log "Java NOT found in PATH!" "ERROR"
        Log "Install JDK from: https://adoptium.net" "ACTION"
        $script:Errors++
    }
    
    if ($env:JAVA_HOME) {
        Log "JAVA_HOME = $env:JAVA_HOME" "OK"
    } else {
        if (Test-Path $script:JdkPath) {
            [System.Environment]::SetEnvironmentVariable("JAVA_HOME", $script:JdkPath, "User")
            $env:JAVA_HOME = $script:JdkPath
            Log "JAVA_HOME set to $($script:JdkPath)" "OK"
        } else {
            Log "JAVA_HOME not set (Gradle will auto-download JDK 21)" "WARN"
            $script:Warnings++
        }
    }
    
    Log "JDK 21 toolchain: auto-provisioned by Gradle (Foojay API)" "INFO"
    
    # ====== STEP 2: ANDROID SDK ======
    Update-Progress "Android SDK" 2
    Log "ANDROID SDK" "HEAD"
    
    $sdk = $script:AndroidSdkPath
    if (Test-Path $sdk) {
        Log "Android SDK found: $sdk" "OK"
    } else {
        Log "Android SDK NOT found!" "ERROR"
        Log "Install Android Studio: https://developer.android.com/studio" "ACTION"
        $script:Errors++
    }
    
    # ANDROID_HOME
    $ahome = [System.Environment]::GetEnvironmentVariable("ANDROID_HOME", "User")
    if (-not $ahome) {
        [System.Environment]::SetEnvironmentVariable("ANDROID_HOME", $sdk, "User")
        $env:ANDROID_HOME = $sdk
        Log "ANDROID_HOME configured: $sdk" "OK"
    } else {
        Log "ANDROID_HOME = $ahome" "OK"
    }
    
    # ANDROID_SDK_ROOT
    $asdkr = [System.Environment]::GetEnvironmentVariable("ANDROID_SDK_ROOT", "User")
    if (-not $asdkr) {
        [System.Environment]::SetEnvironmentVariable("ANDROID_SDK_ROOT", $sdk, "User")
        $env:ANDROID_SDK_ROOT = $sdk
        Log "ANDROID_SDK_ROOT configured" "OK"
    }
    
    # PATH updates
    $userPath = [System.Environment]::GetEnvironmentVariable("Path", "User")
    $addPaths = @("$sdk\platform-tools", "$sdk\emulator", "$sdk\build-tools\36.1.0")
    $changed = $false
    foreach ($p in $addPaths) {
        if ((Test-Path $p -ErrorAction SilentlyContinue) -and ($userPath -notlike "*$p*")) {
            $userPath = "$userPath;$p"
            $changed = $true
            Log "PATH += $(Split-Path $p -Leaf)" "OK"
        }
    }
    if ($changed) {
        [System.Environment]::SetEnvironmentVariable("Path", $userPath, "User")
        $env:Path = "$env:Path;$($addPaths -join ';')"
    }
    
    # Verify platform
    $platDir = "$sdk\platforms\android-37.0"
    if (Test-Path $platDir) {
        Log "Platform android-37.0 (API 37)" "OK"
    } else {
        Log "Platform android-37.0 NOT found" "ERROR"
        $script:Errors++
    }
    
    # ADB
    $adbExe = "$sdk\platform-tools\adb.exe"
    if (Test-Path $adbExe) {
        $adbVer = & $adbExe version 2>&1 | Select-Object -First 1
        Log "ADB: $adbVer" "OK"
    } else {
        Log "ADB not found!" "ERROR"
        $script:Errors++
    }
    
    # Build-tools
    $btDirs = Get-ChildItem "$sdk\build-tools" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Name
    if ($btDirs) {
        Log "Build-tools: $($btDirs -join ', ')" "OK"
    }
    
    # ====== STEP 3: PROJECT CONFIG ======
    Update-Progress "Project Configuration" 3
    Log "PROJECT CONFIGURATION" "HEAD"
    
    # local.properties
    $lp = Join-Path $root "local.properties"
    if (Test-Path $lp) {
        $lpContent = Get-Content $lp -Raw
        if ($lpContent -match "sdk\.dir") {
            Log "local.properties: sdk.dir configured" "OK"
        } else {
            $escaped = $sdk.Replace("\", "\\").Replace(":", "\:")
            Add-Content $lp "`nsdk.dir=$escaped"
            Log "local.properties: sdk.dir added" "OK"
        }
    } else {
        $escaped = $sdk.Replace("\", "\\").Replace(":", "\:")
        "sdk.dir=$escaped" | Set-Content $lp -Encoding UTF8
        Log "local.properties created" "OK"
    }
    
    # google-services.json
    if (Test-Path (Join-Path $root "app\google-services.json")) {
        Log "google-services.json found" "OK"
    } else {
        Log "google-services.json MISSING!" "ERROR"
        $script:Errors++
    }
    
    # keystore
    if (Test-Path (Join-Path $root "app\keystore.jks")) {
        Log "Release keystore (keystore.jks) found" "OK"
    } else {
        Log "Release keystore not found" "WARN"
        $script:Warnings++
    }
    
    # ====== STEP 4: NODE.JS & FIREBASE ======
    Update-Progress "Node.js and Firebase CLI" 4
    Log "NODE.JS AND FIREBASE CLI" "HEAD"
    
    $nodeVer = node --version 2>$null
    if ($nodeVer) {
        Log "Node.js: $nodeVer" "OK"
    } else {
        Log "Node.js not found (optional, for Firebase deploy)" "WARN"
        $script:Warnings++
    }
    
    $npmVer = npm --version 2>$null
    if ($npmVer) {
        Log "npm: v$npmVer" "OK"
    }
    
    $fbVer = firebase --version 2>$null
    if ($fbVer) {
        Log "Firebase CLI: v$fbVer" "OK"
    } else {
        if ($npmVer) {
            Log "Installing Firebase CLI..." "ACTION"
            [System.Windows.Forms.Application]::DoEvents()
            & npm install -g firebase-tools 2>&1 | Out-Null
            $fbVer2 = firebase --version 2>$null
            if ($fbVer2) {
                Log "Firebase CLI installed: v$fbVer2" "OK"
            } else {
                Log "Firebase CLI install failed - try: npm install -g firebase-tools" "WARN"
                $script:Warnings++
            }
        } else {
            Log "Firebase CLI skipped (no npm)" "WARN"
            $script:Warnings++
        }
    }
    
    # ====== STEP 5: GIT ======
    Update-Progress "Git" 5
    Log "GIT" "HEAD"
    
    $gitVer = git --version 2>$null
    if ($gitVer) {
        Log "$gitVer" "OK"
    } else {
        Log "Git not found" "WARN"
        $script:Warnings++
    }
    
    if (Test-Path (Join-Path $root ".gitignore")) {
        Log ".gitignore configured" "OK"
    }
    
    # ====== STEP 6: GRADLE BUILD ======
    Update-Progress "Building Debug APK" 6
    Log "GRADLE BUILD" "HEAD"
    
    $gradlew = Join-Path $root "gradlew.bat"
    if (Test-Path $gradlew) {
        Log "Gradle wrapper found" "OK"
        
        # Debug build
        Log "Building Debug APK... (this may take a few minutes)" "ACTION"
        [System.Windows.Forms.Application]::DoEvents()
        
        Push-Location $root
        $debugOut = & .\gradlew.bat assembleDebug 2>&1 | Out-String
        $debugCode = $LASTEXITCODE
        Pop-Location
        
        if ($debugCode -eq 0 -or $debugOut -match "BUILD SUCCESSFUL") {
            $debugApk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
            if (Test-Path $debugApk) {
                $sz = [math]::Round((Get-Item $debugApk).Length / 1MB, 1)
                Log "Debug APK built successfully ($sz MB)" "OK"
            } else {
                Log "Debug APK build completed" "OK"
            }
        } else {
            Log "Debug APK build FAILED" "ERROR"
            # Show last few lines of output
            $lines = $debugOut -split "`n" | Select-Object -Last 5
            foreach ($l in $lines) {
                $trimmed = $l.Trim()
                if ($trimmed) { Log "  $trimmed" }
            }
            $script:Errors++
        }
    } else {
        Log "gradlew.bat NOT found!" "ERROR"
        $script:Errors++
    }
    
    # ====== STEP 7: RELEASE BUILD ======
    Update-Progress "Building Release APK" 7
    Log "RELEASE BUILD" "HEAD"
    
    if (Test-Path $gradlew) {
        Log "Building Release APK..." "ACTION"
        [System.Windows.Forms.Application]::DoEvents()
        
        Push-Location $root
        $relOut = & .\gradlew.bat assembleRelease 2>&1 | Out-String
        $relCode = $LASTEXITCODE
        Pop-Location
        
        if ($relCode -eq 0 -or $relOut -match "BUILD SUCCESSFUL") {
            $relApk = Join-Path $root "app\build\outputs\apk\release\app-release.apk"
            if (Test-Path $relApk) {
                $sz = [math]::Round((Get-Item $relApk).Length / 1MB, 1)
                Log "Release APK built successfully ($sz MB)" "OK"
                
                # Copy to release folder
                $relDir = Join-Path $root "app\release"
                if (-not (Test-Path $relDir)) { New-Item -ItemType Directory -Path $relDir -Force | Out-Null }
                Copy-Item $relApk (Join-Path $relDir "app-release.apk") -Force
                Log "Copied to app\release\app-release.apk" "INFO"
            }
        } else {
            Log "Release APK build FAILED" "ERROR"
            $script:Errors++
        }
    }
    
    # ====== STEP 8: DEVICES ======
    Update-Progress "Device Detection" 8
    Log "CONNECTED DEVICES" "HEAD"
    
    if (Test-Path $adbExe) {
        $devList = & $adbExe devices 2>&1
        $devs = $devList | Select-String "device$" | Where-Object { $_ -notmatch "List of" }
        
        if ($devs) {
            $devs | ForEach-Object {
                $did = ($_ -split "`t")[0].Trim()
                $model = & $adbExe -s $did shell getprop ro.product.model 2>$null
                $androidVer = & $adbExe -s $did shell getprop ro.build.version.release 2>$null
                if ($model) {
                    Log "$did - $model (Android $androidVer)" "OK"
                } else {
                    Log "$did" "OK"
                }
            }
        } else {
            Log "No devices connected" "WARN"
            Log "Connect via: adb connect <IP>:5555" "INFO"
            $script:Warnings++
        }
    }
    
    # ====== DONE ======
    Update-Progress "Complete" 8
    $progressBar.Value = 100
    
    Log "" "INFO"
    Log "========================================" "HEAD"
    
    if ($script:Errors -eq 0) {
        Log "SETUP COMPLETED SUCCESSFULLY!" "OK"
        $stepLabel.Text = "Setup completed successfully!"
        $stepLabel.ForeColor = [System.Drawing.Color]::FromArgb(80, 220, 120)
    } else {
        Log "SETUP COMPLETED WITH $($script:Errors) ERROR(S)" "ERROR"
        $stepLabel.Text = "Setup completed with $($script:Errors) error(s)"
        $stepLabel.ForeColor = [System.Drawing.Color]::FromArgb(255, 80, 80)
    }
    
    if ($script:Warnings -gt 0) {
        Log "$($script:Warnings) warning(s)" "WARN"
    }
    
    Log "" "INFO"
    Log "Quick commands:" "INFO"
    Log "  Build:   .\gradlew.bat assembleDebug" "INFO"
    Log "  Install: adb install -r app\build\outputs\apk\debug\app-debug.apk" "INFO"
    Log "  Launch:  adb shell am start -n com.luminastreams.tv/.MainActivity" "INFO"
    
    $installBtn.Text = "Done!"
    $installBtn.BackColor = [System.Drawing.Color]::FromArgb(30, 140, 70)
    $browseBtn.Enabled = $true
}

# ==============================================================================
# EVENT HANDLERS
# ==============================================================================

$browseBtn.Add_Click({
    $fbd = New-Object System.Windows.Forms.FolderBrowserDialog
    $fbd.Description = "Select LuminaStreams project directory (contains gradlew.bat)"
    $fbd.ShowNewFolderButton = $false
    if ($script:ProjectRoot) {
        $fbd.SelectedPath = $script:ProjectRoot
    }
    if ($fbd.ShowDialog() -eq "OK") {
        $script:ProjectRoot = $fbd.SelectedPath
        $pathLabel.Text = "Project: $($fbd.SelectedPath)"
        if (Test-Path (Join-Path $fbd.SelectedPath "gradlew.bat")) {
            $pathLabel.ForeColor = [System.Drawing.Color]::FromArgb(80, 220, 120)
        } else {
            $pathLabel.ForeColor = [System.Drawing.Color]::FromArgb(255, 80, 80)
            Log "WARNING: gradlew.bat not found in selected directory!" "WARN"
        }
    }
})

$installBtn.Add_Click({
    Run-Installation
})

$closeBtn.Add_Click({
    $form.Close()
})

# ==============================================================================
# SHOW FORM
# ==============================================================================

Log "Welcome to LuminaStreams Setup!" "HEAD"
Log ""
Log "This installer will configure your Windows development" "INFO"
Log "environment for the LuminaStreams Android TV application." "INFO"
Log ""
Log "What will be configured:" "INFO"
Log "  1. Java JDK validation and JAVA_HOME" "INFO"
Log "  2. Android SDK and environment variables" "INFO"
Log "  3. Project configuration files" "INFO"
Log "  4. Node.js and Firebase CLI" "INFO"
Log "  5. Git verification" "INFO"
Log "  6. Debug APK build" "INFO"
Log "  7. Release APK build" "INFO"
Log "  8. Connected device detection" "INFO"
Log ""
if ($script:ProjectRoot) {
    Log "Project auto-detected: $($script:ProjectRoot)" "OK"
} else {
    Log "Click 'Browse Project...' to select the project folder." "ACTION"
}
Log ""
Log "Click 'Install' to begin." "ACTION"

[void]$form.ShowDialog()
