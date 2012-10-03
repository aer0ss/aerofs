/**
 * Utility functions used by both the packager and the patcher
 */


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
 * Allows a file currently in use by the OS to be patched
 *
 * This works because Windows allows a running EXE or DLL to be renamed
 * (but not deleted or patched). So we rename it to a temporary name, copy
 * it back to its orignal name and patch it.
 */
!macro allowPatchingOfLockedFile Filename
    # Delete all old temp copies
    Delete /REBOOTOK "${Filename}.bak*"

    ${If} ${FileExists} ${Filename}
        # Rename the file with the first available suffix
        !insertmacro renameWithSuffix ${Filename} ".bak"
        ${If} $0 != ""
            # Copy back the file to the orignal name
            CopyFiles $0 ${Filename}
            # Schedule the new temp file for deletion
            Delete /REBOOTOK $0
        ${EndIf}
    ${EndIf}
!macroend


/**
 * Wait until aerofs.jar is unlocked, or until we reach a 20 seconds timeout
 * If we timeout, display an error message to the user and quit
 */
!macro waitForAeroFSJar
    Push  $9
    StrCpy $9 0
    ${Do}
        IntOp $9 $9 + 1
        # If we tried more than 100 times, abort (with 200ms sleep between each attempt => 20s)
        ${If} $9 >= 100
             DetailPrint "Aborting setup because files are locked"
             MessageBox MB_OK|MB_ICONSTOP|MB_TOPMOST "AeroFS couldn't be updated because it is still running \
             in the background. Please restart your computer and try launching AeroFS again.$\n$\n\
             If the problem persists, please email: support@aerofs.com"
             Quit
        ${EndIf}

        !insertmacro isFileLocked "$INSTDIR\aerofs.jar"
        Sleep 200
    ${LoopWhile} $0 == "1"
    Pop $9
!macroend

/**
 * Try to rename a file to a temporary name to see whether the file is locked or not
 * NOTE: This will report a false positive if there is a file named "${Filename}.lock"
 */
!macro isFileLocked Filename
    ClearErrors
    ${If} ${FileExists} "${Filename}"
        Rename ${Filename} "${Filename}.lock"
        ${If} ${Errors}
            DetailPrint "File ${Filename} is locked"
            StrCpy $0 "1"
        ${Else}
            DetailPrint "File ${Filename} NOT locked"
            Rename "${Filename}.lock" ${Filename}
            StrCpy $0 "0"
        ${EndIf}
    ${Else}
        StrCpy $0 "0"
    ${EndIf}
!macroend

/**
 *  Kills all instances of a process owned by the specified user.
 *  Use this macro to avoid killing other users' processes.
*/
!macro KillProcess Process Username
    LogText "killing ${Process} for user ${Username}"
    nsExec::Exec 'taskkill /f /im ${Process} /fi "USERNAME eq ${Username}"'
!macroend
