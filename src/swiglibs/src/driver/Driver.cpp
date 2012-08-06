
#include "../util.h"
#include "../logger.h"
#include "Driver.h"
#include "AeroFS.h"

extern Logger l;

using namespace std;

namespace Driver {

#ifndef _WIN32
volatile bool shutdown = false;
#endif

void initLogger_(jstring rtRoot, jstring name, LogLevel loglevel)
{
    // have to use jstr2tstrNoConversion as AeroFS::init hasn't been called
    tstring tLog;
    AeroFS::jstr2tstrNoConversion(&tLog, jni(), rtRoot);
    tstring tName;
    AeroFS::jstr2tstrNoConversion(&tName, jni(), name);
    tLog.append(SEP);
    tLog.append(tName.c_str());
    l.init_(tLog.c_str(), loglevel);
    l.error() << "=====" << l;
}

}
