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

// Pack an errno (uint32_t) in big-endian format into the first four bytes of
// the buffer (which is guaranteed by the caller to be at least four bytes if
// not null).
// N.B. errsv may itself be a function or macro which we should evaluate only
// once, hence errl
#define PACK_ERROR_IN(buffer, errsv) do {                  \
    if (buffer) {                                          \
        unsigned char * p = (unsigned char *)buffer;       \
        unsigned int errl = errsv;                         \
        p[0] = (unsigned char)((errl & 0xff000000) >> 24); \
        p[1] = (unsigned char)((errl & 0x00ff0000) >> 16); \
        p[2] = (unsigned char)((errl & 0x0000ff00) >>  8); \
        p[3] = (unsigned char)((errl & 0x000000ff) >>  0); \
    }                                                      \
} while(0)

#endif
