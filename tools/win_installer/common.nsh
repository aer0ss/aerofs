/**
 *  Kills all instances of a process owned by the specified user.
 *  Use this macro to avoid killing other users' processes.
*/
!macro KillProcess Process Username
    DetailPrint "Stopping ${Process} for user ${Username}"
    nsExec::Exec 'taskkill /f /im ${Process} /fi "USERNAME eq ${Username}"'
!macroend

/**
 * Unregister the shell extension
 *
 * Note:
 * This should be called with admin privileges in order to fully remove all registry keys that we create.
 * But using this macro without admin privileges will also succeed and will remove most of the keys.
 *
 * Note:
 * In order to figure out which keys need admin privileges to be created or removed, you can look at the *.rgs files
 * in the Shell Extension source code folder. Those named *Admin.rgs define registry keys that need admin privileges.
 */
!macro unregShellExt

    DetailPrint "Unregistering the shell extension"

    # Read the path to the currently registered shell extension in the registry
    Push $0
    SetRegView 32
    ReadRegStr $0 HKCR "CLSID\${AEROFS_SHELLEXT_CLSID}\InprocServer32" ""
    ExecWait 'regsvr32.exe /u /s $0'

    SetRegView 64
    ReadRegStr $0 HKCR "CLSID\${AEROFS_SHELLEXT_CLSID}\InprocServer32" ""
    ExecWait 'regsvr32.exe /u /s $0'
    Pop $0

!macroend

/**
 * Rename a file by appending a suffix and a numeric id to it.
 *
 * If the new file name already exists, the number will be increased
 * until we find an available name or until we reach 100.
 *
 * Example:
 *   !insertmacro renameWithSuffix "c:\test.txt" ".bak"
 *   will rename test.txt to test.txt.bak0.
 *   If there already is a file named test.txt.bak0, we'll try to pick
 *   test.txt.bak1 and so on until 99 inclusive.
 *
 * Returns the new name in $0, or an empty string in case of error
 * (file does not exist or all numbers from 0 to 99 have been tried)
 */
!macro renameWithSuffix Filename Suffix
    Push  $9
    StrCpy $9 0
    ${Do}
        StrCpy $0 "${Filename}${Suffix}$9"
        ClearErrors
        Rename ${Filename} $0
        ${If} ${Errors}
            IntOp $9 $9 + 1
        ${Else}
            ${Break}
        ${EndIf}
    ${LoopUntil} $9 = 100
    ${If} $9 = 100
        StrCpy $0 ""
    ${EndIf}
    Pop $9
!macroend

/**
 * Renames an EXE or DLL currently in use by the OS to a temporary name
 * so that the file can be overwritten.
 *
 * This works because Windows allows a running EXE or DLL to be renamed
 * (but not deleted or patched).
 */
!macro allowOverwritting Filename
    # Delete all old temp copies
    Delete /REBOOTOK "${Filename}.bak*"

    ${If} ${FileExists} ${Filename}
        # Rename the file with the first available suffix
        !insertmacro renameWithSuffix ${Filename} ".bak"
        ${If} $0 != ""
            # Schedule it for deletion
            Delete /REBOOTOK $0
        ${EndIf}
    ${EndIf}
!macroend
