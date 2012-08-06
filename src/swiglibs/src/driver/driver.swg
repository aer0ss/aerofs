%module Driver
%{
#include "../logger.h"
#include "Driver.h"
%}

%include "../aerofs.typemaps.swg"
%include "various.i"

// use SWIG's own JNI object for performance. maybe not needed
%apply JNIEnv *JNI { JNIEnv * j };

// TODO why are they here? compare generated code to figure out
%apply jstring JNI { jstring appRoot, jstring rtRoot, jstring mntpnt, jstring path };

// getFID() may receive a null buffer
%apply char *NULLABLE_BYTE { void * };

%include "../logger.h"
%include "Driver.h"