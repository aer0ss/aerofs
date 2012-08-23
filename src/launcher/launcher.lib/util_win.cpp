#include "util.h"

#include <windows.h>
#include <process.h>
#include <iostream>

static const _TCHAR* const JAR_EXTENSION = _T(".jar");

extern char g_errmsg[256];

#define JRE_KEY              _T("Software\\JavaSoft\\Java Runtime Environment")
#define CURRENT_VERSION_KEY  _T("CurrentVersion")
#define RUNTIMELIB_KEY       _T("RuntimeLib")
#define CREATE_JVM_FUNCTION  "JNI_CreateJavaVM"

tstring find_jvm();
tstring check_key(HKEY jrekey, _TCHAR* subKeyName);

bool file_exists(const tstring& file)
{
    if (FILE* f = _wfopen(file.c_str(), L"r")) {
        fclose(f);
        return true;
    }
    return false;
}

/**
  Calls JNI_CreateJavaVM dynamically, using LoadLibraryW and GetProcAddress
 */
jint create_jvm(JavaVM **pvm, void **penv, void *args)
{
    typedef jint(JNICALL* PJNI_CreateJavaVM)(JavaVM**, void**, void*);

    tstring jvm_path = find_jvm();

    if (jvm_path.empty()) {
        fprintf(stderr, "Could not find a JVM. Please install Java.\n");
        return -1;
    }

    HINSTANCE jvm = LoadLibraryW((LPCWSTR)jvm_path.c_str());
    PJNI_CreateJavaVM fn = (PJNI_CreateJavaVM) GetProcAddress(jvm, CREATE_JVM_FUNCTION);

    if (!fn) {
        fprintf(stderr, "Invalid JVM. Please re-install Java.\n");
        return -1;
    }

    return fn(pvm, penv, args);
}

/**
  Looks in the registry to find a path to the jvm.dll
 */
tstring find_jvm()
{
    HKEY jreKey = NULL;
    DWORD length = MAX_PATH;
    _TCHAR keyName[MAX_PATH];

    if (RegOpenKeyExW(HKEY_LOCAL_MACHINE, JRE_KEY, 0, KEY_READ, &jreKey) == ERROR_SUCCESS) {
        if(RegQueryValueExW(jreKey, CURRENT_VERSION_KEY, NULL, NULL, (LPBYTE)&keyName, &length) == ERROR_SUCCESS) {
            tstring path = check_key(jreKey, keyName);
            if (!path.empty()) {
                RegCloseKey(jreKey);
                return path;
            }
        }

        int i = 0;
        length = MAX_PATH;
        while (RegEnumKeyExW(jreKey, i++, keyName, &length, 0, 0, 0, 0) == ERROR_SUCCESS) {

            // look for a 1.4 or 1.5 vm
            tstring baseVersion = _T("1.4");
            if( baseVersion.compare(0, 3, keyName) <= 0 ) {
                tstring path = check_key(jreKey, keyName);
                if (!path.empty()) {
                    RegCloseKey(jreKey);
                    return path;
                }
            }
        }
        RegCloseKey(jreKey);
    }
    return NULL;
}

/**
  Read the subKeyName subKey of jreKey and look to see if it has a Value
  "RuntimeLib" which points to a jvm library we can use.
  Does not close jreKey.
 */
tstring check_key(HKEY jreKey, _TCHAR* subKeyName)
{
    tstring result;

    _TCHAR value[MAX_PATH];
    HKEY subKey = NULL;
    DWORD length = MAX_PATH;

    if(RegOpenKeyExW(jreKey, subKeyName, 0, KEY_READ, &subKey) == ERROR_SUCCESS) {
        if(RegQueryValueExW(subKey, RUNTIMELIB_KEY, NULL, NULL, (LPBYTE)&value, &length) == ERROR_SUCCESS) {
            if (file_exists(value)) {
                result = value;
            }
        }
        RegCloseKey(subKey);
    }

    return result;
}

/**
  Return a string with the path of all *.jar files at `jars_path` concatenated
  Important:
    - jars_path must include a trailing slash
 */
tstring list_jars(const tstring& jars_path)
{
    // If no trailing separator was given, append one
    tstring jars_clean_path = jars_path;
    if (jars_clean_path[jars_clean_path.length() - 1] != DIRECTORY_SEPARATOR) {
        jars_clean_path += DIRECTORY_SEPARATOR;
    }
    tstring result;
    WIN32_FIND_DATA findData;
    tstring jars_path_pattern = jars_clean_path + _T("*");
    HANDLE handle = FindFirstFile(jars_path_pattern.c_str(), &findData);
    if (handle == INVALID_HANDLE_VALUE) {
        SET_ERROR("Warning: could not open directory %S\n", jars_clean_path.c_str());
        return result;
    }
    do {
        tstring jar(findData.cFileName);
        if (ends_with(jar, JAR_EXTENSION)) {
            result += PATH_SEPARATOR + jars_clean_path + jar;
        }
    } while (FindNextFile(handle, &findData) != 0);
    DWORD error = GetLastError();
    if (error != ERROR_NO_MORE_FILES) {
        SET_ERROR("Warning: FindNextFile returned %d\n", error);
    }
    FindClose(handle);
    return result;
}
