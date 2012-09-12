#ifndef __AEROFS_UTIL_H__
#define __AEROFS_UTIL_H__

#include <jni.h>

#include <string>
#include <assert.h>

#ifdef _WIN32
#include <windows.h>
#include <tchar.h>
#define SEP _T("\\")
#define SEP_CHAR _T('\\')
#else
typedef char TCHAR;
typedef TCHAR* LPTSTR;
typedef const TCHAR* LPCTSTR;
typedef const char * LPCSTR;
typedef char* LPSTR;
typedef unsigned long DWORD;
typedef unsigned short WORD;
#define _T(a) a
#define _tcslen(a) strlen(a)
#define SEP _T("/")
#define SEP_CHAR _T('/')
#endif

typedef std::basic_string<TCHAR> tstring;
typedef std::basic_ofstream<TCHAR> tofstream;
typedef std::basic_ostringstream<TCHAR> tostringstream;
typedef std::basic_ostream<TCHAR> tostream;

#ifdef UNICODE
#define tcout std::wcout
#else
#define tcout std::cout
#endif

#ifdef _DEBUG
#define verify(a)   assert(a)
#else
#undef verify
#define verify(a)  ((void)(a))
#endif

extern JavaVM * s_jvm;

static inline JavaVM * jvm()
{
    return s_jvm;
}

static inline JNIEnv * jni()
{
    JNIEnv * jni;
    verify(jvm()->AttachCurrentThread((void **)&jni, 0) == 0);
    return jni;
}

// TODO new class gref for global references and delete newGlobal/fromGlobal below

// local reference of jobjects, jclasses, etc
template <class T>
struct lref {
    T _ptr;
    JNIEnv * _j;

    lref(JNIEnv * j)
        : _ptr(0), _j(j) {}

    lref(JNIEnv * j, jobject ptr)
        : _ptr(static_cast<T>(ptr)), _j(j) {}

    // it doesn't make sense to duplicate _local_ references
    //lref(const jref & c)
    //    : _j(c._j), _ptr(c._j->NewLocalRef(c._ptr)) {}

    ~lref()
    {
        release_();
    }

    const T ptr()
    {
        return _ptr;
    }

    lref & operator=(jobject ptr)
    {
        release_();
        _ptr = static_cast<T>(ptr);
        return *this;
    }

    operator const T()
    {
        return _ptr;
    }

    // use j->DeleteGlobalRef() to release the returned value
    // TODO BUGBUG NewGlobalRef may return null if out of memory
    T newGlobal()
    {
        return (T)_j->NewGlobalRef(_ptr);
    }

    void cloneGlobal(T ptr)
    {
        release_();
        _ptr = (T)_j->NewLocalRef(ptr);
    }

private:
    void release_()
    {
        if (_ptr) {
            _j->DeleteLocalRef(_ptr);
            _ptr = 0;
        }
    }
};

struct LocalJNI {
    JNIEnv * _j;

    LocalJNI()
    {
        _j = jni();
        _j->PushLocalFrame(16);
    }

    JNIEnv * operator->()
    {
        return _j;
    }

    operator JNIEnv *()
    {
        return _j;
    }

    virtual ~LocalJNI()
    {
        _j->PopLocalFrame(0);
        //s_jvm->DetachCurrentThread();
    }
};

//This function is only necessary in Windows.
// In OSX/Linux, should already be in UTF8, hopefully...

/*
#ifdef _WIN32
static inline LPSTR tstr2utf8(LPCTSTR tstr)
{
#ifdef UNICODE
	// TODO shall use WCharToUTF8 below. wcstombs_s is not too capable
    size_t size;
    if (wcstombs_s(&size, 0, 0, tstr, 0)) return 0;
    LPSTR str = new char[size];
    if (wcstombs_s(&size, str, size, tstr, size)) return 0;
    else return str;
#endif
}
#endif

static inline void tstr2utf8Done(LPCSTR str)
{
#ifdef UNICODE
    delete[] str;
#endif
}
*/

/*
inline bool WCharToUTF8(
	LPWSTR pszwUniString,
	LPSTR  pszAnsiBuff,
	DWORD  dwAnsiBuffSize
	)
{
	return WideCharToMultiByte(
		CP_UTF8,
		0,
		pszwUniString,
		-1,
		pszAnsiBuff,
		dwAnsiBuffSize,
		NULL,
		NULL
		) != 0;
}

inline bool UTF8ToUnicode(
    LPCSTR  pszAnsiString,
    LPWSTR pszwUniBuff,
    DWORD dwUniBuffSize
    )
{
	return MultiByteToWideChar(
		CP_UTF8,
		0,
		pszAnsiString,
		-1,
		pszwUniBuff,
		dwUniBuffSize
		) != 0;
}
*/

#endif
