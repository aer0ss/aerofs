#
# Visual C++ redistributable helper macros
#
# Author: greg@aerofs.com
#

!include x64.nsh

!macro isVCRedistInstalled

    StrCpy $0 "NO"

    # Check that we have the MSVC 2010 DLLs in the System folder
    ${If} ${FileExists} "$SYSDIR\msvcp100.dll"
    ${AndIf} ${FileExists} "$SYSDIR\msvcr100.dll"
    ${AndIf} ${FileExists} "$SYSDIR\atl100.dll"
        # If running a 64-bits OS, we also check that we have the 32-bits version of the DLLs
        ${If} ${RunningX64}
            ${If} ${FileExists} "$WINDIR\SysWoW64\msvcp100.dll"
            ${AndIf} ${FileExists} "$WINDIR\SysWoW64\msvcr100.dll"
            ${AndIf} ${FileExists} "$WINDIR\SysWoW64\atl100.dll"
                StrCpy $0 "YES"
            ${EndIf}
        ${Else}
            StrCpy $0 "YES"
        ${EndIf}
    ${EndIf}

!macroend

!macro checkAndinstallVCRedists

    DetailPrint "Checking MSVC redistributables..."
    !insertmacro isVCRedistInstalled
    ${If} $0 == "NO"
        ${If} ${RunningX64}
            !insertmacro _installRedist "vcredist_x64.exe" "http://download.microsoft.com/download/3/2/2/3224B87F-CFA0-4E70-BDA3-3DE650EFEBA5/vcredist_x64.exe"
        ${EndIf}
        !insertmacro _installRedist "vcredist_x86.exe" "http://download.microsoft.com/download/5/B/C/5BC5DBB3-652D-4DCE-B14A-475AB85EEF6E/vcredist_x86.exe"
    ${EndIf}

    # Checking again
    !insertmacro isVCRedistInstalled
    ${If} $0 == "NO"
        DetailPrint "WARNING: MSVC redistributables not installed."
    ${Else}
        DetailPrint "MSVC redistributables successfully installed."
    ${EndIf}

!macroend


!macro _installRedist RedistName RedistURL

    DetailPrint "Downloading ${RedistName} from ${RedistURL}..."
    InetLoad::load "${RedistURL}" "$TEMP\${RedistName}" /END
    Pop $0 # return value = exit code, "OK" if OK
    ${If} $0 != "OK"
        DetailPrint "WARNING: Download of ${RedistName} failed! Proceeding anyway. Error: $0"
    ${EndIf}

    DetailPrint "Installing ${RedistName}"
    ClearErrors
    ExecWait "$TEMP\${RedistName} /passive /norestart /q:a" $0
    ${If} ${Errors}
        DetailPrint "Failed to launch ${RedistName}"
    ${Else}
        DetailPrint "${RedistName} returned $0"
    ${EndIf}
    Delete "$TEMP\${RedistName}"

!macroend
