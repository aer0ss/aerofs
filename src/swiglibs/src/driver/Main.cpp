
#include <stdio.h>
#include <stdlib.h> /* for exit() */
#include <jni.h>
#include "../logger.h"
#include "AeroFS.h"

Logger l;
JavaVM * s_jvm;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM * vm, void *reserved)
{
    s_jvm = vm;

    if (!AeroFS::init_()) {
        fprintf(stderr, "AeroFS::init_ failed. exit now\n");
        exit(1234);
    }

    return JNI_VERSION_1_2;
}
