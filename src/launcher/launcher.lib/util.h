#ifndef UTIL_H
#define UTIL_H

#include <cstring>
#include <string>
#include <jni.h>
#include "liblauncher.h"
#if WIN32
#define DIRECTORY_SEPARATOR _T('\\')
#define PATH_SEPARATOR _T(';')
#define snprintf _sntprintf_s
#define _sprintf swprintf
#else
#include <unistd.h>         /* for readlink(2) used in liblauncher.cpp */
#define DIRECTORY_SEPARATOR '/'
#define PATH_SEPARATOR ':'
#define _sprintf snprintf
#endif

#define SET_ERROR(...) snprintf(g_errmsg, sizeof(g_errmsg), ##__VA_ARGS__);

typedef std::basic_string<_TCHAR> tstring;

// Implemented in util.cpp
bool ends_with(const tstring& str, const tstring& end);
tstring dir_name(const tstring& path);

// Implemented in the platform-specific util
bool file_exists(const tstring& file);
bool create_jvm(const tstring& approot, JavaVM **pvm, void **penv, void *args);
tstring list_jars(const tstring& jars_path);

#endif // UTIL_H
