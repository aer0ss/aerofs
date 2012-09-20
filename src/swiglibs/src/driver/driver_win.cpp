
#include <windows.h>
#include <psapi.h> // for GetProcessImageFileName

#include "Driver.h"
#include "AeroFS.h"
#include "../logger.h"
#include "strsafe.h"
#include <shlobj.h>
#include <shlwapi.h>
#include <iphlpapi.h>

using namespace std;

namespace Driver {

int getFidLength()
{
    return sizeof(DWORD) * 2;
}

int getFid(JNIEnv * j, jstring jpath, void * buffer)
{
    tstring path;
    if (!AeroFS::jstr2tstr(&path, j, jpath)) return DRIVER_FAILURE;

    HANDLE h = CreateFile(path.c_str(),
        0,
        FILE_SHARE_READ | FILE_SHARE_WRITE,
        0,
        OPEN_EXISTING,
        FILE_FLAG_BACKUP_SEMANTICS, // needed to open directories
        0);
    if (h == INVALID_HANDLE_VALUE) {
        FWARN("1: " << GetLastError());
        return DRIVER_FAILURE;
    }

    BY_HANDLE_FILE_INFORMATION info;
    BOOL ret = GetFileInformationByHandle(h, &info);
    CloseHandle(h);

    if (!ret) {
        FWARN("2: " << GetLastError());
        return DRIVER_FAILURE;
    }

    if (buffer) {
        DWORD * p = (DWORD *) buffer;
        p[0] = info.nFileIndexLow;
        p[1] = info.nFileIndexHigh;
    }

    return info.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY ?
        GETFID_DIR : GETFID_FILE;
}

// These two functions relating to mount ids are only available on OSX and
// Linux, so we stub them out here.
int getMountIdLength() {
    assert(false);
    return false;
}

int getMountIdForPath(JNIEnv * j, jstring jpath, void * buffer)
{
    assert(false);
    return false;
}

int getFileSystemType(JNIEnv * j, jstring jpath, void * buf, int bufLen)
{
    tstring path;
    if (!AeroFS::jstr2tstr(&path, j, jpath)) {
        FWARN("jstr2tstr failed");
        return DRIVER_FAILURE;
    }

    TCHAR root[MAX_PATH + 1];
    if (!GetVolumePathName(path.c_str(), root, MAX_PATH + 1)) {
        FWARN("GVPN: " << GetLastError());
        return DRIVER_FAILURE;
    }

    if (!GetVolumeInformation(root, 0, 0, 0, 0, 0, (LPTSTR) buf, bufLen / sizeof(TCHAR))) {
        FWARN("GVI: " << GetLastError());
        return DRIVER_FAILURE;
    }

    UINT driveType = 0;
    driveType = GetDriveType(root);
    int retval;
    switch (driveType) {
        case DRIVE_UNKNOWN:
            FINFO("GDT: UNKNOWN");
            retval = DRIVER_FAILURE;
            break;
        case DRIVE_NO_ROOT_DIR:
            FINFO("GDT: INVALID");
            retval = DRIVER_FAILURE;
            break;
        case DRIVE_REMOVABLE:
            FINFO("GDT: REMOVABLE");
            // Removable drives can vanish at any time.  We may want to handle this
            // differently in the future.
            retval = FS_LOCAL;
            break;
        case DRIVE_FIXED:
            FINFO("GDT: FIXED");
            retval = FS_LOCAL;
            break;
        case DRIVE_REMOTE:
            FINFO("GDT: REMOTE");
            retval = FS_REMOTE;
            break;
        case DRIVE_CDROM:
            FINFO("GDT: CDROM");
            // We probably don't want to deal with files on optical drives.
            retval = DRIVER_FAILURE;
            break;
        case DRIVE_RAMDISK:
            FINFO("GDT: RAMDISK");
            // DF: I don't know where ramdisk files get used on Windows, so I'm
            // not supporting them.  If this breaks something reasonable, we
            // can revisit that decision.
            retval = DRIVER_FAILURE;
            break;
        default:
            FWARN("GDT: bad rc:" << driveType);
            retval = DRIVER_FAILURE;
            break;
    }
    return retval;
}

void setFolderIcon(JNIEnv* env, jstring folderPath, jstring iconName)
{
    tstring path;
    tstring icon;

    if (!(AeroFS::jstr2tstr(&path, env, folderPath) &&
          AeroFS::jstr2tstr(&icon, env, iconName))) {
        return;
    }

    tstring desktopIni = path + TEXT("\\desktop.ini");

    if (icon.length() > 0) {
        // Set the icon

        PathMakeSystemFolder(path.c_str());

        // Split the "path\to\icon,index" string into iconPath and iconIndex
        TCHAR* buf = new TCHAR[icon.length() + 1];
        copy(icon.begin(), icon.end(), buf);
        buf[icon.length()] = '\0';
        int iconIndex = PathParseIconLocation(buf);
        tstring iconPath(buf);
        delete buf;

        // Create the content of the desktop.ini file
        tostringstream desktopIniContent;
        desktopIniContent << TEXT("[.ShellClassInfo]\r\n")
                             TEXT("IconResource=") << icon << TEXT("\r\n")
                             TEXT("IconFile=") << iconPath << TEXT("\r\n")
                             TEXT("IconIndex=") << iconIndex << TEXT("\r\n");

        tstring content = desktopIniContent.str();

        // Write it to the disk
        HANDLE hFile = CreateFile(desktopIni.c_str(), GENERIC_WRITE, FILE_SHARE_READ, NULL,
                                  CREATE_ALWAYS, FILE_ATTRIBUTE_HIDDEN | FILE_ATTRIBUTE_SYSTEM, NULL);
        DWORD dwBytesWritten = 0;
        WORD Unicode = 0xfeff; // UNICODE BOM
        WriteFile(hFile, &Unicode, 2, &dwBytesWritten, NULL);
        WriteFile(hFile, content.c_str(), content.length() * sizeof(TCHAR), &dwBytesWritten, NULL);
        CloseHandle(hFile);

    } else {
        // Remove the icon
        PathUnmakeSystemFolder(path.c_str());
        DeleteFile(desktopIni.c_str());
    }

    // Tell the shell to refresh the icon
    SHChangeNotify(SHCNE_UPDATEITEM, SHCNF_PATH | SHCNF_FLUSHNOWAIT, path.c_str(), NULL);
}

int waitForNetworkInterfaceChange()
{
    DWORD ret = NotifyAddrChange(NULL, NULL);
    if (ret == NO_ERROR) {
        return DRIVER_SUCCESS;
    } else {
        FWARN("NAC failed " << ret << " " << GetLastError());
        return DRIVER_FAILURE;
    }
}

/**
 * If the process specified by pid is the daemon process, it is killed.
 *
 * @param pid The PID of the process in question
 * @return -1 if the process was not the daemon process, 0 if the process
 *         is the daemon process and was killed successfully and 1 if the
 *         process is the daemon process and failed to exit.
 */
static int killProcessIfDaemon(DWORD pid)
{
    HANDLE hProcess = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ |
            PROCESS_TERMINATE | SYNCHRONIZE, FALSE, pid);
    if (!hProcess) {
        return -1;
    }

    HMODULE hMod;
    DWORD bytesNeeded;
    if (!EnumProcessModules(hProcess, &hMod, sizeof(hMod), &bytesNeeded)) {
        CloseHandle(hProcess);
        return -1;
    }

    TCHAR processName[MAX_PATH];
    if (!GetModuleBaseName(hProcess, hMod, processName, sizeof(processName) / sizeof(TCHAR))) {
        CloseHandle(hProcess);
        return -1;
    }

    if (_tcscmp(processName, _T(DAEMON_PROC_NAME) _T(".exe"))) {
        CloseHandle(hProcess);
        return -1;
    }

    if (!TerminateProcess(hProcess, 0)) {
        FWARN("TP error " << GetLastError());
        CloseHandle(hProcess);
        return 1;
    }

    DWORD ret = WaitForSingleObject(hProcess, INFINITE);
    if (ret != WAIT_OBJECT_0) {
        FWARN("WFSO ret " << ret << " error " << GetLastError());
        CloseHandle(hProcess);
        return 1;
    }

    CloseHandle(hProcess);
    return 0;
}

int killDaemon()
{
    DWORD *processes;
    DWORD bytesNeeded, numProcesses;
    size_t maxBufferSize = 512;

    while (true) {
        // Allocate some memory
        processes = new DWORD[maxBufferSize];

        // Enumerate the processes
        if (!EnumProcesses(processes, maxBufferSize * sizeof(DWORD), &bytesNeeded)) {
            FERROR(" failed to enumerate processes: "<< GetLastError());
            exit(1);
        }

        numProcesses = bytesNeeded / sizeof(DWORD);

        // MSDN specifies that EnumProcesses doesn't report a lack of memory when returning
        // processes. If the number of processes is exactly the same as the size of the
        // given buffer, chances are it is truncated and more memory should be used.
        // http://msdn.microsoft.com/en-us/library/windows/desktop/ms682629(v=vs.85).aspx
        if (numProcesses < maxBufferSize) {
            break;
        }

        delete[] processes;
        maxBufferSize *= 2;
    }

    int numDaemonsKilled = 0;
    bool error = false;
    for (DWORD i = 0; i < numProcesses; i++) {
        if (processes[i] != 0) {
            int result = killProcessIfDaemon(processes[i]);
            if (result == 1) {
                error = true;
            } else if (result == 0) {
                numDaemonsKilled++;
            }
        }
    }

    delete[] processes;

    return error ? DRIVER_FAILURE : numDaemonsKilled;
}

//////////////////////////////////////////////////////////////////////////////////////////////////
// Functions related to finding the position of our tray icon
//////////////////////////////////////////////////////////////////////////////////////////////////

struct WindowToFind
{
    TCHAR* windowName;
    HWND hWnd;
};

/**
 * Callback function for EnumChildWindows
 * Given a pointer to a struct WindowToFind in lParam, it will check that the window class name
 * matches the one in the struct, and return the window handle in the struct
 * Note: this returns the _last_ window that matches. This is needed because on Windows 7 there can
 * be two ToolbarWindow32, the first for the regular system tray icons, the second for temporarily
 * promoted icons (icons that stay there for 45 seconds and then disappear into the overflow area).
 */
BOOL CALLBACK enumChildWindowCallback(HWND hWnd, LPARAM lParam)
{
    WindowToFind* params = (WindowToFind*) lParam;
    TCHAR szClassName[256];
    GetClassName(hWnd, szClassName, sizeof(szClassName));
    if (_tcscmp(szClassName, params->windowName) == 0) {
        params->hWnd = hWnd;
    }
    return TRUE;
}

/**
 * Returns the the handle to the tray window, or NULL if failed.
 */
HWND getTrayWindow()
{
    HWND trayWnd = FindWindow(_T("Shell_TrayWnd"), NULL);

    if (trayWnd) {
        WindowToFind trayNotify = {_T("TrayNotifyWnd"), NULL};
        EnumChildWindows(trayWnd, enumChildWindowCallback, (LPARAM) &trayNotify);

        if (trayNotify.hWnd && IsWindow(trayNotify.hWnd)) {
            WindowToFind toolbarWnd = {_T("ToolbarWindow32"), NULL};
            EnumChildWindows(trayNotify.hWnd, enumChildWindowCallback, (LPARAM) &toolbarWnd);
            if (toolbarWnd.hWnd) {
                return toolbarWnd.hWnd;
            }
        }
    }
    return NULL;
}

TrayPosition getTrayPosition()
{
    int iconWidth = GetSystemMetrics(SM_CXSMICON);

    // Get the orientation of the task bar
    APPBARDATA abd = {0};
    abd.cbSize = sizeof(APPBARDATA);
    abd.hWnd = FindWindow(_T("Shell_TrayWnd"), NULL);
    if (!SHAppBarMessage(ABM_GETTASKBARPOS, &abd)) {
        abd.uEdge = ABE_BOTTOM; // In case of failure, use bottom as default
    }

    // Get the rect that corresponds to the tray icon area
    RECT rect = {0};
    GetWindowRect(getTrayWindow(), &rect);

    OSVERSIONINFO version = {0};
    version.dwOSVersionInfoSize = sizeof(OSVERSIONINFO);
    GetVersionEx(&version);

    // Don't add any margin on Windows XP
    const int HORIZONTAL_MARGIN = (version.dwMajorVersion > 5) ? 6 : 0;
    const int VERTICAL_MARGIN   = (version.dwMajorVersion > 5) ? 3 : 0;

    TrayPosition result;
    switch (abd.uEdge) {
    case ABE_TOP:
        result.orientation = TrayPosition::Top;
        result.x = rect.left + iconWidth / 2 + HORIZONTAL_MARGIN;
        result.y = rect.bottom;
        break;
    case ABE_RIGHT:
        result.orientation = TrayPosition::Right;
        result.x = rect.left;
        result.y = rect.top + iconWidth / 2 + VERTICAL_MARGIN;
        break;
    case ABE_BOTTOM:
        result.orientation = TrayPosition::Bottom;
        result.x = rect.left + iconWidth / 2 + HORIZONTAL_MARGIN;
        result.y = rect.top;
        break;
    case ABE_LEFT:
        result.orientation = TrayPosition::Left;
        result.x = rect.right;
        result.y = rect.top + iconWidth / 2 + VERTICAL_MARGIN;
        break;
    }

    return result;
}

}  // namespace Driver
