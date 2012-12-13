#include "util.h"

#include <windows.h>
#include <process.h>
#include <iostream>

static const _TCHAR* const JAR_EXTENSION = _T(".jar");

extern _TCHAR g_errmsg[256];

#define JRE_KEY              _T("Software\\JavaSoft\\Java Runtime Environment")
#define CURRENT_VERSION_KEY  _T("CurrentVersion")
#define RUNTIMELIB_KEY       _T("RuntimeLib")
#define CREATE_JVM_FUNCTION  "JNI_CreateJavaVM"

tstring find_jvm();
tstring check_key(HKEY jrekey, _TCHAR* subKeyName);

bool file_exists(const tstring& file)
{
    FILE* f;
    if (_tfopen_s(&f, file.c_str(), _T("r")) == 0) {
        fclose(f);
        return true;
    }
    return false;
}

/**
  Calls JNI_CreateJavaVM dynamically, using LoadLibraryW and GetProcAddress
 */
bool create_jvm(JavaVM **pvm, void **penv, void *args)
{
    typedef jint(JNICALL* PJNI_CreateJavaVM)(JavaVM**, void**, void*);

    tstring jvm_path = find_jvm();

    if (jvm_path.empty()) {
        SET_ERROR(_T("%s\n%s%s"),
                  _T("Java 32-bit was not found."),
                  _T("Please download the 32 bit version of Java at "),
                  _T("www.java.com/getjava/"));
        return false;
    }

    HINSTANCE jvm = LoadLibraryW((LPCWSTR)jvm_path.c_str());
    PJNI_CreateJavaVM fn = (PJNI_CreateJavaVM) GetProcAddress(jvm, CREATE_JVM_FUNCTION);

    if (!fn) {
        SET_ERROR(_T("%s %s %s\n%s"),
                  _T("AeroFS found a Java installation at:"),
                  jvm_path.c_str(),
                  _T("but could not use it."),
                  _T("Please visit www.java.com/getjava/ to reinstall Java 32-bit on your computer."));
        return false;
    }

    jint result = fn(pvm, penv, args);
    if (result < 0) {
        SET_ERROR(_T("Call to JNI_CreateJavaVM failed with error code: %ld."), result);
        return false;
    }
    return true;
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
    return _T("");
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
        SET_ERROR(_T("Warning: could not open directory %s\n"), jars_clean_path.c_str());
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
        SET_ERROR(_T("Warning: FindNextFile returned %d\n"), error);
    }
    FindClose(handle);
    return result;
}
