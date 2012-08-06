#include "util.h"

#include <unistd.h>

using namespace std;

/**
  This file implements functions from util.h
  that are common to both Mac OS X and Linux.
*/


jint create_jvm(JavaVM **pvm, void **penv, void *args)
{
    return JNI_CreateJavaVM(pvm, penv, args);
}

/**
  Spawns a new process
  If successful, returns the pid of the process.
  Otherwise, returns -1 and errno is set to the appropriate error code
 */
int spawn(const string& program, char* argv[])
{
    int pid = fork();
    if (pid == 0) {
        return execv(program.c_str(), argv);
    } else {
        return pid;
    }
}
