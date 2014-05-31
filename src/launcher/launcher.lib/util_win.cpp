#include "util.h"

#include <windows.h>
#include <process.h>
#include <iostream>

static const _TCHAR* const JAR_EXTENSION = _T(".jar");

extern _TCHAR g_errmsg[256];

#define CREATE_JVM_FUNCTION  "JNI_CreateJavaVM"

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
bool create_jvm(const tstring& approot, JavaVM **pvm, void **penv, void *args)
{
    typedef jint(JNICALL* PJNI_CreateJavaVM)(JavaVM**, void**, void*);

    tstring jvm_path = approot + _T("\\jre\\bin\\server\\jvm.dll");

    HINSTANCE jvm = LoadLibraryW((LPCWSTR)jvm_path.c_str());
    if (!jvm) {
        SET_ERROR(_T("Could not load jvm at: %s.\nLoadLibraryW failed with %d\n"),
                  jvm_path.c_str(), GetLastError());
        return false;
    }

    PJNI_CreateJavaVM fn = (PJNI_CreateJavaVM) GetProcAddress(jvm, CREATE_JVM_FUNCTION);
    if (!fn) {
        SET_ERROR(_T("Could not load jvm at: %s.\nGetProcAddress failed with %d\n"),
                  jvm_path.c_str(), GetLastError());
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
