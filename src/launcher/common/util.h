#ifndef UTIL_H
#define UTIL_H

#include <cstring>
#include <string>

#ifdef _WIN32
#include <tchar.h>
#include <windows.h>
#define _sprintf swprintf
#else
#include <unistd.h>      /* chdir(2) used in misc.cpp */
#define _TCHAR char
#define _sprintf snprintf
#endif

#define MAX_APPROOT_LENGTH 1024
#define MAX_MESSAGE_LENGTH 1024

typedef std::basic_string<_TCHAR> tstring;

// Utility functions shared by aerofs and aerofsd
bool change_dir(const _TCHAR* path);

#endif // UTIL_H
