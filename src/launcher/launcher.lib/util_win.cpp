#include "util.h"

#include <windows.h>
#include <process.h>

#define JRE_KEY              "Software\\JavaSoft\\Java Runtime Environment"
#define CURRENT_VERSION_KEY  "CurrentVersion"
#define RUNTIMELIB_KEY       "RuntimeLib"
#define CREATE_JVM_FUNCTION  "JNI_CreateJavaVM"

using namespace std;

string find_jvm();
string checkKey(HKEY jrekey, char* subKeyName);

/**
  Calls JNI_CreateJavaVM dynamically, using LoadLibraryA and GetProcAddress
 */
jint create_jvm(JavaVM **pvm, void **penv, void *args)
{
    typedef jint(JNICALL* PJNI_CreateJavaVM)(JavaVM**, void**, void*);

    string jvm_path = find_jvm();

    if (jvm_path.empty()) {
        fprintf(stderr, "Could not find a JVM. Please install Java.\n");
        return -1;
    }

    HINSTANCE jvm = LoadLibraryA(jvm_path.c_str());
    PJNI_CreateJavaVM fn = (PJNI_CreateJavaVM) GetProcAddress(jvm, CREATE_JVM_FUNCTION);

    if (!fn) {
        fprintf(stderr, "Invalid JVM. Please re-install Java.\n");
        return -1;
    }

    return fn(pvm, penv, args);
}

/**
  Spawns a new process
  If successful, returns the pid of the process.
  Otherwise, returns -1 and errno is set to the appropriate error code
 */
int spawn(const string& program, char* argv[])
{
    return _spawnv(_P_NOWAIT , program.c_str(), argv);
}

/**
  Looks in the registry to find a path to the jvm.dll
 */
string find_jvm()
{
    HKEY jreKey = NULL;
    DWORD length = MAX_PATH;
    char keyName[MAX_PATH];

    if (RegOpenKeyExA(HKEY_LOCAL_MACHINE, JRE_KEY, 0, KEY_READ, &jreKey) == ERROR_SUCCESS) {
        if(RegQueryValueExA(jreKey, CURRENT_VERSION_KEY, NULL, NULL, (LPBYTE)&keyName, &length) == ERROR_SUCCESS) {
            string path = checkKey(jreKey, keyName);
            if (!path.empty()) {
                RegCloseKey(jreKey);
                return path;
            }
        }

        int i = 0;
        length = MAX_PATH;
        while (RegEnumKeyExA(jreKey, i++, keyName, &length, 0, 0, 0, 0) == ERROR_SUCCESS) {

            // look for a 1.4 or 1.5 vm
            if( strncmp("1.4", keyName, 3) <= 0 ) {
                string path = checkKey(jreKey, keyName);
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
string checkKey(HKEY jreKey, char* subKeyName)
{
    string result;

    char value[MAX_PATH];
    HKEY subKey = NULL;
    DWORD length = MAX_PATH;

    if(RegOpenKeyExA(jreKey, subKeyName, 0, KEY_READ, &subKey) == ERROR_SUCCESS) {
        if(RegQueryValueExA(subKey, RUNTIMELIB_KEY, NULL, NULL, (LPBYTE)&value, &length) == ERROR_SUCCESS) {
            if (file_exists(value)) {
                result = value;
            }
        }
        RegCloseKey(subKey);
    }

    return result;
}
