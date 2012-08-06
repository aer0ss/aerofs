
#include <windows.h>
#include <psapi.h> // for GetProcessImageFileName

#include "Driver.h"
#include "AeroFS.h"
#include "../logger.h"
#include "strsafe.h"
#include <shlobj.h>
#include <Shlwapi.h>
using namespace std;

namespace Driver {

int getPid()
{
    FINFO("");

    assert(sizeof(int) == sizeof(DWORD));
    return GetCurrentProcessId();
}

bool killProcess(int pid)
{
    FINFO("");

    assert(sizeof(int) == sizeof(DWORD));
    HANDLE hProc = OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ |
            PROCESS_TERMINATE | SYNCHRONIZE, false, pid);
    if (!hProc) {
        FWARN("OP "<< pid << " warn " << GetLastError());
        // we should have returned false. but the error might be that
        // the daemon already quit.
        return true;
    }

    TCHAR buf[MAX_PATH];
    if (!GetProcessImageFileName(hProc, buf, MAX_PATH)) {
        FWARN("GPIFN error " << GetLastError());
        return false;
    }

    if (_tcscmp(buf + _tcslen(buf) - 8, _T("java.exe"))) {
        FWARN(" not us: " << buf);
        // ignore the request. hopefully the daemon already quit
        return true;
    }

    if (!TerminateProcess(hProc, 0)) {
        FWARN("TP error " << GetLastError());
        CloseHandle(hProc);
        return false;
    }

    DWORD ret = WaitForSingleObject(hProc, INFINITE);
    if (ret != WAIT_OBJECT_0) {
        FWARN("WFSO ret " << ret << " error " << GetLastError());
        CloseHandle(hProc);
        return false;
    }

    CloseHandle(hProc);
    return true;
}

int getFidLength()
{
    return sizeof(DWORD) * 2;
}

int getFid(JNIEnv * j, jstring jpath, void * buffer)
{
    tstring path;
    if (!AeroFS::jstr2tstr(&path, j, jpath)) return GETFID_ERROR;

    HANDLE h = CreateFile(path.c_str(),
        0,
        FILE_SHARE_READ | FILE_SHARE_WRITE,
        0,
        OPEN_EXISTING,
        FILE_FLAG_BACKUP_SEMANTICS, // needed to open directories
        0);
    if (h == INVALID_HANDLE_VALUE) {
        FWARN("1: " << GetLastError());
        return GETFID_ERROR;
    }

    BY_HANDLE_FILE_INFORMATION info;
    BOOL ret = GetFileInformationByHandle(h, &info);
    CloseHandle(h);

    if (!ret) {
        FWARN("2: " << GetLastError());
        return GETFID_ERROR;
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

}  // namespace Driver
