#ifndef UTIL_H
#define UTIL_H

#include <string>
#include <jni.h>

#if WIN32
#define DIRECTORY_SEPARATOR '\\'
#define PATH_SEPARATOR ';'
#else
#define DIRECTORY_SEPARATOR '/'
#define PATH_SEPARATOR ':'
#endif

// Implemented in util.cpp
bool file_exists(const std::string& file);
bool ends_with(const std::string& str, const std::string& end);
std::string dir_name(const std::string& path);

// Implemented in the platform-specific util
jint create_jvm(JavaVM **pvm, void **penv, void *args);
int spawn(const std::string& program, char* argv[]);

#endif // UTIL_H
