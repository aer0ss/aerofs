#
# Visual C++ redistribuatble macros
#
# Author: greg@aerofs.com
#

!include x64.nsh

/**
 *  Declare that you are using a Visual C++ redistributable

 *  Params:
 *  Redist    Name of the redistributable setup file (eg: vcredist_x86.exe)
 *  Version   Version number, used simply to display an appropriate message during install (eg: 2010)
 *  Bits      Either 32 or 64
 *  CLSID     The CLSID of the installer, used to determine is already installed
 *
 *  Note: you can only use at most 10 VC++ redistributables
 */
!macro usingRedist Redist Version Bits CLSID

    !ifndef REDIST_COUNT
        !define REDIST_COUNT 0
    !else
        !define /math REDIST_TMP ${REDIST_COUNT} + 1
        !undef REDIST_COUNT
        !define REDIST_COUNT ${REDIST_TMP}
        !undef REDIST_TMP
    !endif

    !if ${REDIST_COUNT} >= 10
        !error "Can't install more than 10 redistributables."
    !endif

    !define REDIST_NAME${REDIST_COUNT} ${Redist}
    !define REDIST_VERS${REDIST_COUNT} ${Version}
    !define REDIST_BITS${REDIST_COUNT} ${Bits}
    !define REDIST_UUID${REDIST_COUNT} ${CLSID}

!macroend

/**
 *  Checks and install any missing redistributable
 */
!macro checkAndInstallRedists
    !insertmacro _checkAndInstallRedist 0
    !insertmacro _checkAndInstallRedist 1
    !insertmacro _checkAndInstallRedist 2
    !insertmacro _checkAndInstallRedist 3
    !insertmacro _checkAndInstallRedist 4
    !insertmacro _checkAndInstallRedist 5
    !insertmacro _checkAndInstallRedist 6
    !insertmacro _checkAndInstallRedist 7
    !insertmacro _checkAndInstallRedist 8
    !insertmacro _checkAndInstallRedist 9
!macroend

/**
 *  Check if all redists are installed
 *  Returns "1" in $0 is all redists are installed, "0" otherwise
*/
!macro areAllRedistsInstalled
    StrCpy $0 "1"
    !insertmacro _checkRedist 0
    ${IfThen} $1 == "" ${|} StrCpy $0 "0" ${|}
    !insertmacro _checkRedist 1
    ${IfThen} $1 == "" ${|} StrCpy $0 "0" ${|}
    !insertmacro _checkRedist 2
    ${IfThen} $1 == "" ${|} StrCpy $0 "0" ${|}
    !insertmacro _checkRedist 3
    ${IfThen} $1 == "" ${|} StrCpy $0 "0" ${|}
    !insertmacro _checkRedist 4
    ${IfThen} $1 == "" ${|} StrCpy $0 "0" ${|}
    !insertmacro _checkRedist 5
    ${IfThen} $1 == "" ${|} StrCpy $0 "0" ${|}
    !insertmacro _checkRedist 6
    ${IfThen} $1 == "" ${|} StrCpy $0 "0" ${|}
    !insertmacro _checkRedist 7
    ${IfThen} $1 == "" ${|} StrCpy $0 "0" ${|}
    !insertmacro _checkRedist 8
    ${IfThen} $1 == "" ${|} StrCpy $0 "0" ${|}
    !insertmacro _checkRedist 9
    ${IfThen} $1 == "" ${|} StrCpy $0 "0" ${|}
!macroend


!macro _checkAndInstallRedist i
    !ifdef REDIST_NAME${i}
        !insertmacro _checkRedist ${i}
        ${If} $1 == ""
            SetOutPath $TEMP
            File ${REDIST_NAME${i}}
            DetailPrint "Installing VC++ ${Version} ${REDIST_BITS${i}}-bits runtime"
            ExecWait "$TEMP\${REDIST_NAME${i}} /passive /norestart /q:a" $0
            DetailPrint "VC++ ${REDIST_VERS${i}} ${REDIST_BITS${i}}-bits runtime install returned $0"
            Delete $TEMP\${REDIST_NAME${i}}
        ${EndIf}
    !endif
!macroend

/**
 *  Returns "" in $1 if redist i is not installed
 *  Note: 64-bits redistributables will always be reported as _installed_ on 32-bits platforms
*/
!macro _checkRedist i
    !ifdef REDIST_NAME${i}
        ${IfNot} ${RunningX64}
        ${AndIf} ${REDIST_BITS${i}} == 64
            # If x64 redist on 32-bits platform, report installed
            StrCpy $1 "1"
        ${Else}
            StrCpy $1 ""
            SetRegview ${REDIST_BITS${i}}
            ReadRegStr $1 HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${REDIST_UUID${i}}" DisplayName
        ${EndIf}
    !endif
!macroend