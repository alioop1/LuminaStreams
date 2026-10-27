# LuminaStreams - Windows App Launcher
# Starts Android TV emulator, installs and launches LuminaStreams

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

[System.Windows.Forms.Application]::EnableVisualStyles()

# ==============================================================================
# CONFIGURATION
# ==============================================================================

$SDK = "$env:LOCALAPPDATA\Android\Sdk"
$EMULATOR = "$SDK\emulator\emulator.exe"
$ADB = "$SDK\platform-tools\adb.exe"
$AVD_NAME = "Television_4K"
$PACKAGE = "com.luminastreams.tv"
$ACTIVITY = ".MainActivity"

# Find APK - check multiple locations
$APK = ""
$apkCandidates = @()

# Try to find relative to EXE location
try {
    $exePath = [System.Diagnostics.Process]::GetCurrentProcess().MainModule.FileName
    if ($exePath) {
        $exeDir = [System.IO.Path]::GetDirectoryName($exePath)
        $apkCandidates += Join-Path $exeDir "app-release.apk"
        $apkCandidates += Join-Path $exeDir "app\release\app-release.apk"
        $apkCandidates += Join-Path $exeDir "app\build\outputs\apk\release\app-release.apk"
        $apkCandidates += Join-Path $exeDir "app\build\outputs\apk\debug\app-debug.apk"
        # If EXE is in installer subfolder, go up
        $parentDir = [System.IO.Path]::GetDirectoryName($exeDir)
        if ($parentDir) {
            $apkCandidates += Join-Path $parentDir "app\release\app-release.apk"
            $apkCandidates += Join-Path $parentDir "app\build\outputs\apk\release\app-release.apk"
            $apkCandidates += Join-Path $parentDir "app\build\outputs\apk\debug\app-debug.apk"
        }
    }
} catch {}

# Hardcoded known paths
$apkCandidates += "C:\Users\TechPC\Documents\LuminaStreams\app\release\app-release.apk"
$apkCandidates += "C:\Users\TechPC\Documents\LuminaStreams\app\build\outputs\apk\release\app-release.apk"
$apkCandidates += "C:\Users\TechPC\Documents\LuminaStreams\app\build\outputs\apk\debug\app-debug.apk"

foreach ($candidate in $apkCandidates) {
    if ($candidate -and (Test-Path $candidate)) {
        $APK = $candidate
        break
    }
}

# ==============================================================================
# SPLASH SCREEN
# ==============================================================================

$splash = New-Object System.Windows.Forms.Form
$splash.Text = "LuminaStreams"
$splash.Size = New-Object System.Drawing.Size(480, 280)
$splash.StartPosition = "CenterScreen"
$splash.FormBorderStyle = "None"
$splash.BackColor = [System.Drawing.Color]::FromArgb(12, 12, 20)
$splash.ShowInTaskbar = $true
$splash.TopMost = $true
$splash.Opacity = 0.97

# Rounded look via region
$splash.Add_Paint({
    $g = $_.Graphics
    $g.SmoothingMode = "AntiAlias"
    $g.Clear([System.Drawing.Color]::FromArgb(12, 12, 20))
    
    # Gradient-like top bar
    $brush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(25, 20, 50))
    $g.FillRectangle($brush, 0, 0, 480, 4)
    $brush2 = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(100, 60, 220))
    $g.FillRectangle($brush2, 0, 0, 480, 2)
})

# Title
$splashTitle = New-Object System.Windows.Forms.Label
$splashTitle.Text = "LUMINA STREAMS"
$splashTitle.Font = New-Object System.Drawing.Font("Segoe UI", 28, [System.Drawing.FontStyle]::Bold)
$splashTitle.ForeColor = [System.Drawing.Color]::FromArgb(130, 100, 255)
$splashTitle.TextAlign = "MiddleCenter"
$splashTitle.Location = New-Object System.Drawing.Point(0, 40)
$splashTitle.Size = New-Object System.Drawing.Size(480, 55)
$splash.Controls.Add($splashTitle)

# Subtitle
$splashSub = New-Object System.Windows.Forms.Label
$splashSub.Text = "Android TV on Windows"
$splashSub.Font = New-Object System.Drawing.Font("Segoe UI", 10)
$splashSub.ForeColor = [System.Drawing.Color]::FromArgb(120, 120, 150)
$splashSub.TextAlign = "MiddleCenter"
$splashSub.Location = New-Object System.Drawing.Point(0, 95)
$splashSub.Size = New-Object System.Drawing.Size(480, 25)
$splash.Controls.Add($splashSub)

# Status label
$statusLabel = New-Object System.Windows.Forms.Label
$statusLabel.Text = "Starting emulator..."
$statusLabel.Font = New-Object System.Drawing.Font("Segoe UI", 9)
$statusLabel.ForeColor = [System.Drawing.Color]::FromArgb(180, 160, 255)
$statusLabel.TextAlign = "MiddleCenter"
$statusLabel.Location = New-Object System.Drawing.Point(0, 150)
$statusLabel.Size = New-Object System.Drawing.Size(480, 22)
$splash.Controls.Add($statusLabel)

# Progress bar
$prog = New-Object System.Windows.Forms.ProgressBar
$prog.Location = New-Object System.Drawing.Point(60, 185)
$prog.Size = New-Object System.Drawing.Size(360, 6)
$prog.Style = "Marquee"
$prog.MarqueeAnimationSpeed = 30
$splash.Controls.Add($prog)

# Cancel button
$cancelBtn = New-Object System.Windows.Forms.Button
$cancelBtn.Text = "Cancel"
$cancelBtn.Size = New-Object System.Drawing.Size(80, 30)
$cancelBtn.Location = New-Object System.Drawing.Point(200, 220)
$cancelBtn.FlatStyle = "Flat"
$cancelBtn.BackColor = [System.Drawing.Color]::FromArgb(40, 40, 60)
$cancelBtn.ForeColor = [System.Drawing.Color]::FromArgb(150, 150, 170)
$cancelBtn.FlatAppearance.BorderColor = [System.Drawing.Color]::FromArgb(60, 60, 80)
$cancelBtn.Font = New-Object System.Drawing.Font("Segoe UI", 8)
$cancelBtn.Cursor = "Hand"
$splash.Controls.Add($cancelBtn)

$script:cancelled = $false
$cancelBtn.Add_Click({
    $script:cancelled = $true
    $splash.Close()
})

function Update-Status {
    param([string]$msg)
    $statusLabel.Text = $msg
    [System.Windows.Forms.Application]::DoEvents()
}

# ==============================================================================
# MAIN LOGIC - runs after splash shows
# ==============================================================================

$splash.Add_Shown({

    # --- Validate tools ---
    if (-not (Test-Path $EMULATOR)) {
        [System.Windows.Forms.MessageBox]::Show(
            "Android Emulator not found at:`n$EMULATOR`n`nPlease install Android SDK via Android Studio.",
            "LuminaStreams - Error", "OK", "Error")
        $splash.Close()
        return
    }
    
    if (-not (Test-Path $ADB)) {
        [System.Windows.Forms.MessageBox]::Show(
            "ADB not found at:`n$ADB`n`nPlease install Android SDK Platform-Tools.",
            "LuminaStreams - Error", "OK", "Error")
        $splash.Close()
        return
    }
    
    if (-not $APK) {
        [System.Windows.Forms.MessageBox]::Show(
            "APK file not found!`n`nPlease place app-release.apk next to this exe,`nor build the project first.",
            "LuminaStreams - Error", "OK", "Error")
        $splash.Close()
        return
    }
    
    Update-Status "Checking emulator status..."
    
    # --- Check if emulator already running ---
    $emulatorAlreadyRunning = $false
    try {
        $devices = & $ADB devices 2>&1 | Out-String
        if ($devices -match "emulator-\d+\s+device") {
            $emulatorAlreadyRunning = $true
            # Get the emulator device ID
            $emulatorDevice = ($devices | Select-String "emulator-\d+" | Select-Object -First 1).Matches[0].Value
        }
    } catch {}
    
    if (-not $emulatorAlreadyRunning) {
        Update-Status "Starting Android TV emulator..."
        
        # Start emulator in background
        $emulatorProcess = Start-Process -FilePath $EMULATOR `
            -ArgumentList "-avd", $AVD_NAME, "-no-boot-anim", "-no-snapshot-save", "-gpu", "auto" `
            -PassThru -WindowStyle Normal
        
        if ($script:cancelled) { return }
        
        # Wait for emulator to boot
        Update-Status "Waiting for emulator to boot..."
        $timeout = 120  # seconds
        $elapsed = 0
        $booted = $false
        
        while ($elapsed -lt $timeout -and -not $script:cancelled) {
            Start-Sleep -Seconds 2
            $elapsed += 2
            
            try {
                $bootResult = & $ADB shell getprop sys.boot_completed 2>$null
                if ($bootResult -and $bootResult.Trim() -eq "1") {
                    $booted = $true
                    break
                }
            } catch {}
            
            $pct = [math]::Min(90, [int]($elapsed / $timeout * 100))
            Update-Status "Booting emulator... ($elapsed`s)"
            [System.Windows.Forms.Application]::DoEvents()
        }
        
        if ($script:cancelled) { return }
        
        if (-not $booted) {
            [System.Windows.Forms.MessageBox]::Show(
                "Emulator failed to boot within $timeout seconds.`nPlease try running it manually from Android Studio.",
                "LuminaStreams - Timeout", "OK", "Warning")
            $splash.Close()
            return
        }
        
        # Give it a moment to fully settle
        Update-Status "Emulator ready, preparing..."
        Start-Sleep -Seconds 3
    } else {
        Update-Status "Emulator already running!"
        Start-Sleep -Seconds 1
    }
    
    if ($script:cancelled) { return }
    
    # --- Install APK ---
    Update-Status "Installing LuminaStreams..."
    [System.Windows.Forms.Application]::DoEvents()
    
    try {
        $installResult = & $ADB install -r -t $APK 2>&1 | Out-String
        if ($installResult -match "Success") {
            Update-Status "Installation successful!"
        } else {
            # Try with -d flag (downgrade)
            $installResult2 = & $ADB install -r -t -d $APK 2>&1 | Out-String
            if ($installResult2 -match "Success") {
                Update-Status "Installation successful!"
            } else {
                Update-Status "Install warning - attempting launch anyway..."
            }
        }
    } catch {
        Update-Status "Install warning - attempting launch anyway..."
    }
    
    Start-Sleep -Seconds 1
    if ($script:cancelled) { return }
    
    # --- Launch app ---
    Update-Status "Launching LuminaStreams..."
    [System.Windows.Forms.Application]::DoEvents()
    
    try {
        & $ADB shell am start -n "$PACKAGE/$ACTIVITY" 2>&1 | Out-Null
    } catch {}
    
    Start-Sleep -Seconds 2
    Update-Status "LuminaStreams is running!"
    Start-Sleep -Seconds 1
    
    # Close splash
    $splash.Close()
})

# Show splash and run
[void]$splash.ShowDialog()
