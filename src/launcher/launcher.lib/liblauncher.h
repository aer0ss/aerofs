#ifndef LAUNCHER_H
#define LAUNCHER_H

#include <cstdlib> /* For getenv(), malloc(), reallocf(), size_t, etc. */

#ifdef _WIN32
#include <tchar.h>
#include <windows.h>
#else
#define _TCHAR char
#define _T(X) X
#define _tcscpy_s(X, Y, Z) strncpy(X, Z, Y)
#define _tcscmp(X, Y) strcmp(X, Y)
#define _tprintf(...) printf(__VA_ARGS__)
#define _ftprintf(...) fprintf(__VA_ARGS__)
#endif

bool launcher_get_approot(_TCHAR* approot, size_t approot_len, _TCHAR** perrmsg, bool fromOSXBundle);
bool launcher_create_jvm(const _TCHAR* approot, _TCHAR** args, JavaVM** pjvm, JNIEnv** penv, _TCHAR** perrmsg);
int launcher_launch(JNIEnv* env, _TCHAR** perrmsg);
void launcher_destroy_jvm(JavaVM* jvm);

#endif // LAUNCHER_H
