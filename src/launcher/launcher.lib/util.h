#ifndef UTIL_H
#define UTIL_H

#include <string>
#include <jni.h>
#include "liblauncher.h"

#if WIN32
#define DIRECTORY_SEPARATOR _T('\\')
#define PATH_SEPARATOR _T(';')
#define snprintf sprintf_s
#else
#define DIRECTORY_SEPARATOR '/'
#define PATH_SEPARATOR ':'
#endif

#define SET_ERROR(...) snprintf(g_errmsg, sizeof(g_errmsg), ##__VA_ARGS__);

// Implemented in util.cpp
bool ends_with(const tstring& str, const tstring& end);
tstring dir_name(const tstring& path);

// Implemented in the platform-specific util
bool file_exists(const tstring& file);
jint create_jvm(JavaVM **pvm, void **penv, void *args);
tstring list_jars(const tstring& jars_path);

#endif // UTIL_H
