#ifndef __AEROFS_H__
#define __AEROFS_H__

#include "../util.h"

namespace AeroFS {

bool init_();

bool tcs2jstr(lref<jstring> * ret, JNIEnv * j, LPCTSTR tcs);
bool tstr2jstr(lref<jstring> * ret, JNIEnv * j, tstring & tstr);
bool jstr2tstr(tstring * ret, JNIEnv * j, jstring jstr);
bool jstr2tstrNoConversion(tstring * ret, JNIEnv * j, jstring jstr);

}

#endif
