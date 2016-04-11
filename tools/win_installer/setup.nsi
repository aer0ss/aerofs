# AeroFS Installer
#
# The installer requires the following parameters to be specified in command line:
# AEROFS_IN_FOLDER  - Path to the folder whose content is duplicated to the user's machine
# AEROFS_OUT_FILE - The full path on the build server to output the generated installer
# AEROFS_PRODUCT - The product name. e.g. "AeroFS Team Server"
# AEROFS_APPROOT - The name of the APPROOT folder "AeroFSTeamServerExec"
# AEROFS_VERSION - The current version in the form <major>.<minor>.<build>
# AEROFS_EXECUTABLE - The main executable name. e.g. "aerofsts.exe"
#

!AddPluginDir "Plugins"
Name "${AEROFS_PRODUCT}"

# This supposedly enables Unicode support
# Unfortunately it is not available as of NSIS 2.46
#TargetMinimalOS 5.1

RequestExecutionLevel user

#overwrite only if file is accessible; if file is in read only mode, ignore it
SetOverwrite try

# General Symbol Definitions
!define VERSION               "${AEROFS_VERSION}.0"
!define COMPANY               "Air Computing, Inc."
!define URL                    http://www.aerofs.com
!define AEROFS_SHELLEXT_CLSID "{882108B0-26E6-4926-BC70-EA1D738D5DEB}"

# MUI Symbol Definitions
!define MUI_ICON "${AEROFS_IN_FOLDER}\icons\logo.ico"
!define MUI_UNFINISHPAGE_NOAUTOCLOSE

# Included files
!include LogicLib.nsh
!include Library.nsh
!include common.nsh
!include Sections.nsh
!include MUI2.nsh
!include UAC.nsh
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
OutFile "${AEROFS_OUT_FILE}"
CRCCheck on
XPStyle on
ShowInstDetails hide
VIProductVersion "${VERSION}"
VIAddVersionKey ProductName "$(^Name)"
VIAddVersionKey ProductVersion "${VERSION}"
VIAddVersionKey CompanyName "${COMPANY}"
VIAddVersionKey CompanyWebsite "${URL}"
VIAddVersionKey FileVersion "${VERSION}"
VIAddVersionKey FileDescription ""
VIAddVersionKey LegalCopyright ""
InstallDir $APPDATA\"${AEROFS_APPROOT}" # sets INSTDIR. We always installs to %APPDATA%.
ShowUninstDetails hide
AutoCloseWindow true # Automatically close the window at the end of installation

Function requestAdminPrivileges

    !insertmacro UAC_RunElevated

    ${If} $0 = 0
    ${AndIf} $1 = 1
        # We successfuly re-launched the installer with admin privileges, quit this instance
        Quit
    ${EndIf}

    # if (($0 == 0 && $1 == 3) || $0 == 1223)
    ${If} $0 = 0
    ${AndIf} $1 = 3
    ${OrIf} $0 = 1223

        # User declined to give admin privileges.
        StrCpy $9 "If $(^Name) is installed without admin privileges, it may not function properly.\
                   See http://ae.ro/1yxEsNe for more information.$\n\
                   $\n\
                   Install anyway?"
        MessageBox MB_YESNO|MB_ICONQUESTION|mb_TopMost|mb_SetForeground $9 /SD IDYES IDYES proceed IDNO 0

        # The user choose to abort installation:
        MessageBox MB_OK|MB_ICONEXCLAMATION "The installation was aborted."
        Quit

    ${EndIf}

proceed:
    # All other cases, simply try to proceed with the installation
FunctionEnd

/**************************************
 **  Installer
 *************************************/
Function .onInit
    InitPluginsDir

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
 *  If we the user has granted us admin rights, we will call postInstall_privileged
 *  with admin rights, otherwise, will call it as the current user.
 */
Section -Main
    ${If} ${UAC_IsInnerInstance}
        !insertmacro UAC_AsUser_Call Function install_unprivileged ${UAC_SYNCREGISTERS}
        Call postInstall_privileged
    ${Else}
        Call install_unprivileged
        Call postInstall_privileged
    ${EndIf}
SectionEnd

/**
 *  This function will be called in the regular user context.
 *  All install code that doesn't need special privileges should go here.
 *
 *  Note: because of a UAC plugin limitation, no output from this function will be shown.
 *  If you need to debug anything, use a MessageBox.
 */
Function install_unprivileged

    # Kill AeroFS
    !insertmacro KillProcess "${AEROFS_EXECUTABLE}" $USERNAME
    !insertmacro KillProcess "aerofsd.exe" $USERNAME

    # Unregister the Shell Extension
    !insertmacro unregShellExt

    # Delete the previous versions
    ClearErrors
    FindFirst $1 $2 "$INSTDIR\v_*"
    ${Unless} ${Errors}
        ${Do}
            ${If} $2 != "v_${AEROFS_VERSION}"
                DetailPrint "Deleting previous version: $2"
                RMDir /r "$INSTDIR\$2"
            ${EndIf}
            ClearErrors
            FindNext $1 $2
        ${LoopUntil} ${Errors}
        FindClose $1
    ${EndUnless}

    # Delete previous manifest to force fresh download
    Delete "$INSTDIR\manifest.json"

    # Allow overwritting the executables even if they are still in use
    # This should not be the case, but we never know, since they are not in the
    # per-version folder.
    !insertmacro allowOverwritting "$INSTDIR\${AEROFS_EXECUTABLE}"

    # Copy files
    DetailPrint "Copying files..."
    SetShellVarContext current
    SetOutPath $INSTDIR
    File /r ${AEROFS_IN_FOLDER}\*

    # Create the uninstaller and the shortcuts
    WriteUninstaller "$INSTDIR\uninstall.exe"
    SetOutPath "$SMPROGRAMS\$(^Name)"
    CreateShortcut "$SMPROGRAMS\$(^Name)\Uninstall $(^Name).lnk" $INSTDIR\uninstall.exe
    CreateShortcut "$SMSTARTUP\$(^Name).lnk" $INSTDIR\${AEROFS_EXECUTABLE}
    CreateShortcut "$SMPROGRAMS\$(^Name)\$(^Name).lnk" $INSTDIR\${AEROFS_EXECUTABLE}

    # Write uninstall registry keys
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\$(^Name)" "DisplayIcon" "$INSTDIR\${AEROFS_EXECUTABLE},0"
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

    # Allow the executables to go through the Windows Firewall
    SimpleFC::AddApplication "AeroFS" "$USERS_INSTDIR\${AEROFS_EXECUTABLE}" 0 2 "" 1
    SimpleFC::AddApplication "AeroFS GUI" "$USERS_INSTDIR\current\${AEROFS_EXECUTABLE}" 0 2 "" 1
    SimpleFC::AddApplication "AeroFS Daemon" "$USERS_INSTDIR\current\aerofsd.exe" 0 2 "" 1

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
    Exec "$INSTDIR\${AEROFS_EXECUTABLE}"
FunctionEnd


/**************************************
 **  Uninstaller
 *************************************/

Function un.onInit

    !insertmacro UAC_RunElevated
    ${If} $0 = 0
    ${AndIf} $1 = 1
        # We successfuly re-launched the installer with admin privileges, quit this instance
        Quit
    ${EndIf}

    ${If} $0 = 0
    ${AndIf} $1 = 3
    ${OrIf} $0 = 1223

        # User declined to give admin privileges.
        StrCpy $9 "Some components may not be fully uninstalled without administrator privileges.$\n\
                   $\n\
                   Uninstall anyway?"
        MessageBox MB_YESNO|MB_ICONQUESTION|mb_TopMost|mb_SetForeground $9 /SD IDYES IDYES proceed IDNO 0

        # The user choose to abort uninstallation:
        MessageBox MB_OK|MB_ICONEXCLAMATION "The uninstallation was aborted."
        Quit

    ${EndIf}
proceed:
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
    # Unregister the shell extension
    !insertmacro unregShellExt
FunctionEnd

Function un.uninstall_unprivileged
    # Quit AeroFS before uninstalling
    UserInfo::GetName
    Pop $0
    !insertmacro KillProcess "${AEROFS_EXECUTABLE}" $0
    !insertmacro KillProcess "aerofsd.exe" $0

    Delete /REBOOTOK "$SMPROGRAMS\$(^Name)\Uninstall $(^Name).lnk"
    Delete /REBOOTOK "$SMPROGRAMS\$(^Name)\$(^Name).lnk"
    Delete "$SMSTARTUP\$(^Name).lnk"
    Delete /REBOOTOK "$INSTDIR\uninstall.exe"
    RmDir /r /REBOOTOK "$SMPROGRAMS\$(^Name)"
    RmDir /r /REBOOTOK "$INSTDIR"

    DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\$(^Name)"
FunctionEnd
