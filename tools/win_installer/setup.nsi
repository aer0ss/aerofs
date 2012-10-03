# AeroFS Installer
#
# The installer requires the following parameters to be specified in command line:
# AEROFS_SETUP_FOLDER - The full path to the folder where the setup package file should be generated
# AEROFS_VERSION - The current version in the form <major>.<minor>.<build>
#

!AddPluginDir "Plugins"
Name AeroFS

# This supposedly enables Unicode support
# Unfortunately it is not available as of NSIS 2.46
#TargetMinimalOS 5.1

RequestExecutionLevel user

#overwrite only if file is accessible; if file is in read only mode, ignore it
SetOverwrite try

# General Symbol Definitions
!define VERSION "${AEROFS_VERSION}.0"
!define COMPANY "Air Computing, Inc."
!define URL http://www.aerofs.com

# MUI Symbol Definitions
!define MUI_ICON aerofs\icons\logo.ico
!define MUI_UNFINISHPAGE_NOAUTOCLOSE

# Java Runtime Environment
!define JRE_VERSION "1.6"
!define JRE_URL "http://javadl.sun.com/webapps/download/AutoDL?BundleId=63691"  # Java 7u4 - url from: http://www.java.com/en/download/manual.jsp

# Included files
!include common.nsh
!include Sections.nsh
!include MUI2.nsh
!include Library.nsh
!include jre.nsh
!include UAC.nsh
!include vcredist.nsh
!include WinVer.nsh

# Variables
Var USERS_INSTDIR
Var USERNAME

# Installer pages
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

# Installer languages
!insertmacro MUI_LANGUAGE English

# Installer attributes
OutFile "${AEROFS_SETUP_FOLDER}\AeroFSInstall-${AEROFS_VERSION}.exe"
CRCCheck on
XPStyle on
ShowInstDetails hide
VIProductVersion "${VERSION}"
VIAddVersionKey ProductName AeroFS
VIAddVersionKey ProductVersion "${VERSION}"
VIAddVersionKey CompanyName "${COMPANY}"
VIAddVersionKey CompanyWebsite "${URL}"
VIAddVersionKey FileVersion "${VERSION}"
VIAddVersionKey FileDescription ""
VIAddVersionKey LegalCopyright ""
InstallDir $APPDATA\AeroFSExec          # Always install to %AppData%\AeroFSExec
ShowUninstDetails hide

!insertmacro UsingRedist vcredist_2010_x64.exe 2010 64 "{DA5E371C-6333-3D8A-93A4-6FD5B20BCC6E}"
!insertmacro UsingRedist vcredist_2010_x86.exe 2010 32 "{196BB40D-1578-3D01-B289-BEFC77A11A1E}"

Function requestAdminPrivileges

uac_tryagain:

    !insertmacro UAC_RunElevated

    ${If} $0 = 0
    ${AndIf} $1 = 1
        # We successfuly re-launched the installer with admin privileges, quit this instance
        Quit
    ${EndIf}

    # if ( ($0 == 0 && $1 == 3) || $0 == 1223 )
    ${If} $0 = 0
    ${AndIf} $1 = 3
    ${OrIf} $0 = 1223
        # User declined to give admin privileges. Offer to retry
        Call isAdminRequired
        ${If} $0 <> 0
            StrCpy $9 "AeroFS cannot be installed without administrator rights. \
                Would you like to try entering your administrator password again? \
                If you click no, the installation will be canceled."
            MessageBox MB_YESNO|mb_IconExclamation|mb_TopMost|mb_SetForeground $9 /SD IDNO IDYES uac_tryagain IDNO 0
            Quit # If the user chooses not te retry, quit
        ${Else}
            StrCpy $9 "AeroFS installer needs administrator rights to install some features. \
                Would you like to try entering your administrator password again? \
                If you click no, those features will be disabled."
            MessageBox MB_YESNO|mb_IconExclamation|mb_TopMost|mb_SetForeground $9 /SD IDNO IDYES uac_tryagain IDNO 0
            Return # If the user chooses not to retry, continue without admin privileges
        ${EndIf}
    ${EndIf}

    # All other cases, simply try to proceed with the installation
FunctionEnd

/**
Checks if we can install without admin privileges (at the cost of some features such as icon overlay),
or if we absolutely need admin rights (to install the JRE, for example)

Returns: $0 = "0" if we can continue without being admin, "1" otherwise
*/
Function isAdminRequired
    # Require admin if JRE is not installed
    Push "${JRE_VERSION}"
    Call DetectJRE
    Pop $0
    Pop $1
    ${If} $0 != "OK"
        StrCpy $0 "1"
        Return
    ${EndIf}

    # Require admin if some redistributables need to be installed
    !insertmacro areAllRedistsInstalled
    ${If} $0 != "1"
        StrCpy $0 "1"
        Return
    ${EndIf}

    # End of checks, admin is not required
    StrCpy $0 "0"
    Return
FunctionEnd

/**************************************
 **  Installer
 *************************************/
Function .onInit
    # Enables logging to $INSTDIR\install.log
    # Caveat: this will only log operations performed under unprivileged sections
    # Sections running with elevated privileges will not have their output logged
    #
    # NOTE: If you get a NSIS compile error on this line, this means you're using
    # a default makensis compiler without logging support enabled. In this case,
    # follow the instructions in "compiling NSIS linux.txt" under the TEAM/docs folder
    # to get a makensis with logging support.

    LogSet on
    LogText "Installing AeroFS ${VERSION}..."

    # Save original user info so that we can retrieve it later while running as admin
    UserInfo::GetName
    Pop $0
    StrCpy $USERNAME $0
    StrCpy $USERS_INSTDIR $INSTDIR

    # Do not ask for admin privileges if we're running in silent mode
    # We run in silent mode when we're auto updating from a previous version
    ${IfNot} ${Silent}
        Call requestAdminPrivileges
    ${EndIf}
FunctionEnd

/**
 *  This is the main section
 *  If we the user has granted us admin rights, we will call preInstall_privileged and postInstall_privileged
 *  functions with admin rights, otherwise, will call them as the current user.
 */
Section -Main
    ${If} ${UAC_IsInnerInstance}
        Call preInstall_privileged
        !insertmacro UAC_AsUser_Call Function install_unprivileged ${UAC_SYNCREGISTERS}
        Call postInstall_privileged
    ${Else}
        Call preInstall_privileged
        Call install_unprivileged
        Call postInstall_privileged
    ${EndIf}
SectionEnd

/**
 *  pre-install
 *  Code in this function may or may not run with admin privileges, depending on whether the user has
 *  granted us admin rights. Even if we aren't admin, we should try nonetheless and fail silently.
 */
Function preInstall_privileged

    InitPluginsDir

    call DownloadAndInstallJREIfNecessary

    # Install the VC++ runtime libraries
    !insertmacro checkAndInstallRedists

FunctionEnd

/**
 *  This function will be called in the regular user context.
 *  All install code that doesn't need special privileges should go here.
 *
 *  Note: because of a UAC plugin limitation, no output from this function will be shown.
 *  If you need to debug anything, use a MessageBox.
 */
Function install_unprivileged

    # Try to quit AeroFS before installing
    !insertmacro KillProcess "aerofs.exe" $USERNAME
    !insertmacro KillProcess "aerofsd.exe" $USERNAME

    # Make it possible to overwrite a previous version of the shell extension
    !insertmacro allowPatchingOfLockedFile "$INSTDIR\AeroFSShellExt32.dll"
    !insertmacro allowPatchingOfLockedFile "$INSTDIR\AeroFSShellExt64.dll"

    # Note
    # We temporarily need to do this because the installer is also being used to
    # update and we haven't implemented the one-folder-per-version solution
    # TODO: Remove this block of code once it's done
    !insertmacro waitForAeroFSJar
    !insertmacro allowPatchingOfLockedFile "$INSTDIR\aerofsd.exe"
    !insertmacro allowPatchingOfLockedFile "$INSTDIR\aerofsd.dll"
    !insertmacro allowPatchingOfLockedFile "$INSTDIR\aerofsj.dll"
    !insertmacro allowPatchingOfLockedFile "$INSTDIR\aerofsjn.dll"
    !insertmacro allowPatchingOfLockedFile "$INSTDIR\sqlitejdbc.dll"

    # Copy files
    SetShellVarContext current
    SetOutPath $INSTDIR
    File /r aerofs\*

    WriteUninstaller $INSTDIR\uninstall.exe
    SetOutPath $SMPROGRAMS\AeroFS
    CreateShortcut "$SMPROGRAMS\AeroFS\Uninstall $(^Name).lnk" $INSTDIR\uninstall.exe
    CreateShortcut "$SMSTARTUP\$(^Name).lnk" $INSTDIR\aerofs.exe
    CreateShortcut "$SMPROGRAMS\AeroFS\$(^Name).lnk" $INSTDIR\aerofs.exe

    # Write uninstall registry keys
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\$(^Name)" "DisplayIcon" "$INSTDIR\aerofs.exe,0"
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\$(^Name)" "DisplayName" "$(^Name)"
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\$(^Name)" "HelpLink" "${URL}"
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\$(^Name)" "InstallLocation" "$INSTDIR"
    WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\$(^Name)" "NoModify" 0x1
    WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\$(^Name)" "NoRepair" 0x1
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\$(^Name)" "Publisher" "${COMPANY}"
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\$(^Name)" "UninstallString" "$INSTDIR\uninstall.exe"
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\$(^Name)" "URLInfoAbout" "${URL}"

FunctionEnd

/**
 *  post-install
 *  Code in this function may or may not run with admin privileges, depending on whether the user has
 *  granted us admin rights. Even if we aren't admin, we should try nonetheless and fail silently.
 */
Function postInstall_privileged

    # Get the non-admin's $INSTDIR.
    ${If} ${UAC_IsInnerInstance}
        !insertmacro UAC_AsUser_GetGlobalVar $USERS_INSTDIR
        !insertmacro UAC_AsUser_GetGlobalVar $USERNAME
    ${EndIf}

    # Allow java.exe and aerofs.exe to go through the Windows Firewall
    SimpleFC::AddApplication "AeroFS" "$USERS_INSTDIR\aerofs.exe" 0 2 "" 1
    SimpleFC::AddApplication "AeroFS Daemon" "$USERS_INSTDIR\aerofsd.exe" 0 2 "" 1

    # Register the shell extension
    DetailPrint "Registering the shell extension"
    ExecWait 'regsvr32.exe /s "$USERS_INSTDIR\AeroFSShellExt32.dll"'
    ExecWait 'regsvr32.exe /s "$USERS_INSTDIR\AeroFSShellExt64.dll"'

    # Relaunch explorer on Windows XP - otherwise it won't detect that we added a new shell extension
    # But do not relaunch on updates (silent mode) otherwise Explorer would be restarted too often
    ${IfNot} ${Silent}
    ${AndIf} ${IsWinXP}
        !insertmacro KillProcess "explorer.exe" $USERNAME
        Exec 'C:\Windows\explorer.exe' # It's important to specify the full name, otherwise the 32-bits explorer is launched on x64 systems, because the installer is a 32-bits process
    ${EndIf}

FunctionEnd

/**
 *  Called after a successful installation, when the user closes the installer
 */
Function .onInstSuccess
    # Switch to unprivileged mode
    !insertmacro UAC_AsUser_Call Function onInstSuccess_unprivileged ${UAC_SYNCREGISTERS}
FunctionEnd

Function onInstSuccess_unprivileged
    # Launch AeroFS
    Exec "$INSTDIR\aerofs.exe"
FunctionEnd


/**************************************
 **  Uninstaller
 *************************************/

Var UN_USERS_INSTDIR

Function un.onInit

    # Save the user's instdir so that we can retrieve it later while running as admin
    StrCpy $UN_USERS_INSTDIR $INSTDIR

    uac_tryagain:
        !insertmacro UAC_RunElevated
        ${If} $0 = 0
        ${AndIf} $1 = 1
            # We successfuly re-launched the installer with admin privileges, quit this instance
            Quit
        ${EndIf}

        ${If} $0 = 0
        ${AndIf} $1 = 3
        ${OrIf} $0 = 1223
            # User declined to give admin privileges. Offer to retry
            StrCpy $9 "Some components may not be fully uninstalled without administrator rights. \
                Would you like to try entering your administrator password again?"
            MessageBox MB_YESNO|mb_IconExclamation|mb_TopMost|mb_SetForeground $9 /SD IDNO IDYES uac_tryagain IDNO 0
        ${EndIf}

FunctionEnd

Section -un.Main
    ${If} ${UAC_IsInnerInstance}
        Call un.preUninstall_privileged
        !insertmacro UAC_AsUser_Call Function un.uninstall_unprivileged ${UAC_SYNCREGISTERS}
    ${Else}
        Call un.preUninstall_privileged
        Call un.uninstall_unprivileged
    ${EndIf}

SectionEnd

Function un.preUninstall_privileged
    # Get the non-admin's $INSTDIR.
    ${If} ${UAC_IsInnerInstance}
        !insertmacro UAC_AsUser_GetGlobalVar $UN_USERS_INSTDIR
    ${EndIf}

    # Unregister the shell extension
    DetailPrint "Unregistering the shell extension"
    ExecWait 'regsvr32.exe /u /s "$UN_USERS_INSTDIR\AeroFSShellExt32.dll"'
    ExecWait 'regsvr32.exe /u /s "$UN_USERS_INSTDIR\AeroFSShellExt64.dll"'
FunctionEnd

Function un.uninstall_unprivileged
    Delete /REBOOTOK "$SMPROGRAMS\AeroFS\Uninstall $(^Name).lnk"
    Delete /REBOOTOK "$SMPROGRAMS\AeroFS\$(^Name).lnk"
    Delete "$SMSTARTUP\$(^Name).lnk"
    Delete /REBOOTOK $INSTDIR\uninstall.exe
    RmDir /r /REBOOTOK $SMPROGRAMS\AeroFS
    RmDir /r /REBOOTOK $INSTDIR

    DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\$(^Name)"
FunctionEnd
