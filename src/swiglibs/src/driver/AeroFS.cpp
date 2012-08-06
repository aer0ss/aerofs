#ifndef _WIN32
#include <sys/errno.h>
#include <stdlib.h>
#endif

#include "AeroFS.h"
#include "../util.h"
#include "../logger.h"

// NB: CK* macros may directly return. remember to reclaim resources properly.

// only check return values
#define CK(ret) \
    do {    \
        if (!(ret)) { \
            FINFO( " ex at " << __FILE__ << ':' << dec << __LINE__ << hex );   \
            return false;   \
        }   \
    } while (0)

// only check exceptions
#define CKE(j) \
    do {    \
        if ((j)->ExceptionCheck()) { \
            FINFO( " ex at " << __FILE__ << ':' << dec << __LINE__ << hex );   \
            return false;   \
        }   \
    } while (0)

// check both return values and exceptions, useful for Java methods that may return null
#define CKRE(ret, j) \
    do {    \
        if (!(ret) && (j)->ExceptionCheck()) { \
            FINFO( " ex at " << __FILE__ << ':' << dec << __LINE__ << hex );   \
            return false;   \
        }   \
    } while (0)

using namespace std;

namespace AeroFS {

////////
// classes

jclass s_clsString;

////////
// methods and fields

jmethodID s_midString_getBytes;
jmethodID s_cidString;

////////
// constants

jstring s_constString_UTF8;

bool init_()
{
    JNIEnv * j = jni();

    ////////
    // classes

    lref<jclass> clsString(j, j->FindClass("java/lang/String"));
    CK(clsString);
    s_clsString = clsString.newGlobal();

    ////////
    // methods

    s_midString_getBytes = j->GetMethodID(clsString, "getBytes", "(Ljava/lang/String;)[B");
    CK(s_midString_getBytes);

    s_cidString = j->GetMethodID( clsString, "<init>", "([BLjava/lang/String;)V");
    CK(s_cidString);

    ////////
    // constants

    lref<jstring> utf8(j, j->NewStringUTF("UTF8"));
    CK(utf8);
    s_constString_UTF8 = utf8.newGlobal();

    return true;
}

static inline bool String_getBytes(lref<jbyteArray>* ret, JNIEnv *j, jstring instr, jstring charset)
{
	*ret = j->CallObjectMethod(instr, s_midString_getBytes, charset);
	CK(*ret);
	return true;
}

static inline bool newString(lref<jstring>* ret, JNIEnv *j, jbyteArray source, jstring charset)
{
	*ret = j->NewObject(s_clsString, s_cidString, source, charset);
	CK(*ret);
	return true;
}

bool tcs2jstr(lref<jstring> * ret, JNIEnv * j, LPCTSTR tcs)
{
#ifdef _UNICODE
    assert(sizeof(TCHAR) == sizeof(jchar));
    *ret = j->NewString(reinterpret_cast<const jchar *>(tcs), _tcslen(tcs));
    return *ret != 0;

#else
	int len;
	if (j->EnsureLocalCapacity(2) < 0) {
		FWARN("tcs2jstr: out of memory error");
		return false;
	}

    len = _tcslen(tcs);
	lref<jbyteArray> bytes(j, j->NewByteArray(len));
	if (!bytes) {
		FWARN("tcs2jstr: out of memory error");
		return false;
    }

	j->SetByteArrayRegion( bytes, 0, len, (jbyte *)tcs);
	newString(ret, j, bytes, s_constString_UTF8);
	CKE(j);
	return true;
#endif
}

bool tstr2jstr(lref<jstring> * ret, JNIEnv * j, tstring & tstr)
{
    return tcs2jstr(ret, j, tstr.c_str());
}

// this method is used mainly for
// 1) convert jstr to cstr before AeroFS::init() is called, and
// 2) to perform conversion for unicode platforms (i.e. Windows)
//
bool jstr2tstrNoConversion(tstring * ret, JNIEnv * j, jstring jstr)
{
    // can't directly use the return string as it's not null-terminated.
    // See http://java.sun.com/docs/books/jni/html/pitfalls.html Sec. 10.8
    LPCTSTR tcs;

#ifdef _UNICODE
    tcs = reinterpret_cast<LPCTSTR>(j->GetStringCritical(jstr, 0));
#else
    tcs = j->GetStringUTFChars(jstr, 0);
#endif

    if (!tcs) return false;
    ret->assign(tcs, j->GetStringLength(jstr));

#ifdef _UNICODE
    j->ReleaseStringCritical(jstr, reinterpret_cast<const jchar *>(tcs));
#else
    j->ReleaseStringUTFChars(jstr, tcs);
#endif
    return true;
}

bool jstr2tstr(tstring * ret, JNIEnv * j, jstring jstr)
{
#ifdef _UNICODE
    assert(sizeof(TCHAR) == sizeof(jchar));
    return jstr2tstrNoConversion(ret, j, jstr);

#else

	lref<jbyteArray> bytes(j);
	CK(AeroFS::String_getBytes(&bytes, j, jstr, s_constString_UTF8));

	jint len = j->GetArrayLength(bytes);
	// TODO avoid heap allocation if possible
	LPTSTR result = new TCHAR[len];
	if (!result) {
		FWARN("jstr2tstr: out of memory");
		return false;
	}

	j->GetByteArrayRegion(bytes, 0, len, (jbyte *) result);
	ret->assign(result, len);
	delete[] result;

	return true;
#endif
}

}
