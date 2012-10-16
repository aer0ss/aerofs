# AeroFS Installer
#
# The installer requires the following parameters to be specified in command line:
# AEROFS_IN_FOLDER  - Path to the folder that is to be installed on the user's machine
# AEROFS_OUT_FOLDER - The full path to the folder where the setup package file should be generated
# AEROFS_VERSION    - The current version in the form <major>.<minor>.<build>
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
!define VERSION               "${AEROFS_VERSION}.0"
!define COMPANY               "Air Computing, Inc."
!define URL                    http://www.aerofs.com
!define AEROFS_SHELLEXT_CLSID "{882108B0-26E6-4926-BC70-EA1D738D5DEB}"

# MUI Symbol Definitions
!define MUI_ICON "${AEROFS_IN_FOLDER}\icons\logo.ico"
!define MUI_UNFINISHPAGE_NOAUTOCLOSE

# Java Runtime Environment
!define JRE_VERSION "1.6"
!define JRE_URL "http://javadl.sun.com/webapps/download/AutoDL?BundleId=63691"  # Java 7u4 - url from: http://www.java.com/en/download/manual.jsp

# Included files
!include 'LogicLib.nsh'
!include Library.nsh
!include vcredist.nsh
!include common.nsh
!include Sections.nsh
!include MUI2.nsh
!include jre.nsh
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
OutFile "${AEROFS_OUT_FOLDER}\AeroFSInstall-${AEROFS_VERSION}.exe"
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

# Sanity check: make sure that the aerofs.ini has been created
# This step is done by the build script, by SED'ing the version number into a template aerofs.ini
# If this step fails or we forget to do it for some reason, AeroFS will not launch
${!defineifexist} AEROFS_DOT_INI_EXISTS "${AEROFS_IN_FOLDER}\aerofs.ini"
!ifndef AEROFS_DOT_INI_EXISTS
    !error "Missing aerofs.ini in ${AEROFS_IN_FOLDER}. Aborting."
!endif
!undef AEROFS_DOT_INI_EXISTS

Function requestAdminPrivileges

uac_tryagain:

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
    !insertmacro checkAndinstallVCRedists

FunctionEnd


/**
 *  This function will be called in the regular user context.
 *  All install code that doesn't need special privileges should go here.
 *
 *  Note: because of a UAC plugin limitation, no output from this function will be shown.
 *  If you need to debug anything, use a MessageBox.
 */
Function install_unprivileged

    # Kill AeroFS
    !insertmacro KillProcess "aerofs.exe" $USERNAME
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

    # Delete the old AeroFS files before we switched to the one-folder-per-version model
    # TODO: This code can be removed after, say, April 2013 (6 months from now)
    RMDir /r /REBOOTOK "$INSTDIR\bin"
    RMDir /r /REBOOTOK "$INSTDIR\icons"
    RMDir /r /REBOOTOK "$INSTDIR\lib"
    Delete /REBOOTOK "$INSTDIR\aerofs.jar"
    Delete /REBOOTOK "$INSTDIR\shortcut.exe"
    Delete /REBOOTOK "$INSTDIR\aerofsd.dll"
    Delete /REBOOTOK "$INSTDIR\aerofsj.dll"
    Delete /REBOOTOK "$INSTDIR\aerofsjn.dll"
    Delete /REBOOTOK "$INSTDIR\AeroFSShellExt32.dll"
    Delete /REBOOTOK "$INSTDIR\AeroFSShellExt64.dll"
    Delete /REBOOTOK "$INSTDIR\sqlitejdbc.dll"
    Delete /REBOOTOK "$INSTDIR\version"
    Delete /REBOOTOK "$INSTDIR\cacert.pem"
    Delete /REBOOTOK "$INSTDIR\cacert-ci.pem"

    # Allow overwritting aerofs.exe and aerofsd.exe even if they are still in use
    # This should not be the case, but we never know, since they are not in the
    # per-version folder.
    !insertmacro allowOverwritting "$INSTDIR\aerofs.exe"
    !insertmacro allowOverwritting "$INSTDIR\aerofsd.exe"

    # Copy files
    DetailPrint "Copying files..."
    SetShellVarContext current
    SetOutPath $INSTDIR
    File /r ${AEROFS_IN_FOLDER}\*

    # Create the uninstaller and the shortcuts
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

    # Allow aerofs.exe and aerofsd.exe to go through the Windows Firewall
    SimpleFC::AddApplication "AeroFS" "$USERS_INSTDIR\aerofs.exe" 0 2 "" 1
    SimpleFC::AddApplication "AeroFS Daemon" "$USERS_INSTDIR\aerofsd.exe" 0 2 "" 1

    # Register the shell extension
    DetailPrint "Registering the shell extension"
    ExecWait 'regsvr32.exe /s "$USERS_INSTDIR\v_${AEROFS_VERSION}\AeroFSShellExt32.dll"'
    ExecWait 'regsvr32.exe /s "$USERS_INSTDIR\v_${AEROFS_VERSION}\AeroFSShellExt64.dll"'

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

Function un.onInit

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
    # Unregister the shell extension
    !insertmacro unregShellExt
FunctionEnd

Function un.uninstall_unprivileged
    # Quit AeroFS before uninstalling
    UserInfo::GetName
    Pop $0
    !insertmacro KillProcess "aerofs.exe" $0
    !insertmacro KillProcess "aerofsd.exe" $0

    Delete /REBOOTOK "$SMPROGRAMS\AeroFS\Uninstall $(^Name).lnk"
    Delete /REBOOTOK "$SMPROGRAMS\AeroFS\$(^Name).lnk"
    Delete "$SMSTARTUP\$(^Name).lnk"
    Delete /REBOOTOK $INSTDIR\uninstall.exe
    RmDir /r /REBOOTOK $SMPROGRAMS\AeroFS
    RmDir /r /REBOOTOK $INSTDIR

    DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\$(^Name)"
FunctionEnd
