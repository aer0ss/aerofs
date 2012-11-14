#ifndef __AEROFS_LOGGER_H__
#define __AEROFS_LOGGER_H__

#include <iostream>
#include <fstream>
#include <string>
#include <string.h>
#include <sstream>
#include <stdio.h>
#include "util.h"

#ifndef _WIN32
#include <pthread.h>
#include <sys/time.h>
using namespace std;
#else
#include <windows.h>
#endif

enum LogLevel { LDEBUG = 0, LINFO, LWARN, LERROR };

#ifndef SWIG
#define FDEBUG(x)   do { if (l.loglevel() <= LDEBUG) l.debug() << __FUNCTION__ << x << l; } while (0)
#define FINFO(x)    do { if (l.loglevel() <= LINFO)  l.info()  << __FUNCTION__ << x << l; } while (0)
#define FWARN(x)    do { if (l.loglevel() <= LWARN)  l.warn()  << __FUNCTION__ << x << l; } while (0)
#define FERROR(x)   do { if (l.loglevel() <= LERROR) l.error() << __FUNCTION__ << x << l; } while (0)


class Logger;
extern Logger l;

class Logger {

    friend void operator <<(tostream & s, Logger & l);

private:

#ifdef _WIN32
    CRITICAL_SECTION _mx;
    long _lastArchiveDate;
#else
    pthread_mutex_t _mx;
    tm* _lastArchiveDate;
#endif
    tofstream _f;
    int _cnt;
    tstring _path;
    LogLevel _loglevel;


#ifdef _WIN32
    long getDate(SYSTEMTIME & st)
    {
        FILETIME ft;
        SystemTimeToFileTime(&st, &ft);
        ULARGE_INTEGER uli;
        uli.LowPart = ft.dwLowDateTime; // could use memcpy here!
        uli.HighPart = ft.dwHighDateTime;
        ULONGLONG date(uli.QuadPart / 10000 / 1000 / 60 / 60 / 24);
        return (long) date;
    }
#else
    void getDate(tm **tm)
    {
        struct timeval tv;
        struct timezone tz;
        gettimeofday(&tv,&tz);
        *tm=localtime(&tv.tv_sec);
    }
#endif

    bool archive_()
    {
#ifdef _WIN32
        SYSTEMTIME time;
        GetLocalTime(&time);
        tostringstream os;
        os.fill('0');
        os.setf(std::ios::right);
        os.width(4);
        os << time.wYear;
        os.width(2);
        os << time.wMonth;
        os.width(2);
        os << time.wDay;
#else
        struct timeval tv;
        struct timezone tz;
        struct tm *tm;
        gettimeofday(&tv, &tz);
        tm = localtime(&tv.tv_sec);

        tostringstream os;
        os.fill('0');
        os.setf(std::ios::right);
        os.width(4);
        os << tm->tm_year + 1900; // year since 1900
        os.width(2);
        os << tm->tm_mon+1; //month since January (0-11)
        os.width(2);
        os << tm->tm_mday;

        //free(tm);
#endif
        tstring pathArch = _path;
        pathArch += '.';
        pathArch += os.str();

        std::wcerr << "archiving " << _path.c_str() << " => " << pathArch.c_str() << std::endl;

#ifdef _WIN32
        if (!MoveFile(_path.c_str(), pathArch.c_str())) {
#else
        if (!rename(_path.c_str(), pathArch.c_str())) {
#endif
            std::cerr << "cannot archive log" << std::endl;
            return false;
        } else {
            return true;
        }
    }

    // NB the default format for logging is in hex
    tostream & log()
    {
        // print time
#ifdef _WIN32
        SYSTEMTIME st;
        GetLocalTime(&st);

        tostringstream os;
        os.fill('0');
        os.setf(std::ios::right);
        os.width(2);
        os << st.wHour;
        os.width(2);
        os << st.wMinute;
        os.width(2);
        os << st.wSecond << '.';
        os.width(3);
        os << st.wMilliseconds;
#else
        struct timeval tv;
        struct timezone tz;
        struct tm *tm;
        gettimeofday(&tv, &tz);
        tm = localtime(&tv.tv_sec);

        ostringstream os;
        os.fill('0');
        os.setf(std::ios::right);
        os.width(2);
        os << tm->tm_hour;
        os.width(2);
        os << tm->tm_min;
        os.width(2);
        os << tm->tm_sec << '.';
        os.width(3);
        os << tv.tv_usec;

        //free(tm);
#endif
        // print thread
        //os << " [" << os.hex << GetCurrentThreadId() << os.dec << "] ";
#ifdef _WIN32
        EnterCriticalSection(&_mx);

        if (_path.empty()) return tcout << os.str();
#else
        pthread_mutex_lock(&_mx);
        if (_path.empty()) return cout << os.str();
#endif

        if (_f.fail()) _f.clear();

        if (_cnt++ > 200) {
            _cnt = 0;

#ifdef _WIN32
            long today = getDate(st);
            if (today != _lastArchiveDate) {
#else
			struct tm *today;
			getDate(&today);
			if(today->tm_year != _lastArchiveDate->tm_year &&
                    today->tm_mon != _lastArchiveDate->tm_mon &&
                    today->tm_mday != _lastArchiveDate->tm_mday ) {
#endif

                _f.close();

#ifdef _WIN32
				if (archive_()) _lastArchiveDate = today;
#else
				if (archive_()) memcpy(_lastArchiveDate,today,sizeof(struct tm));
#endif
                // don't check errors here as 1) we can't return error
                // 2) it's unlikely to fail
                _f.open(_path.c_str(), std::ios_base::out | std::ios_base::app);
                _f << std::hex;
            }
        }

        return _f << os.str();
    }

    void end(tostream & s)
    {
        s << std::endl;
#ifdef _WIN32
        LeaveCriticalSection(&_mx);
#else
        pthread_mutex_unlock(&_mx);
#endif
    }

public:

    Logger()
    {
#ifdef _WIN32
    	InitializeCriticalSection(&_mx);

        SYSTEMTIME st;
        GetLocalTime(&st);
        _lastArchiveDate = getDate(st);
#else
        pthread_mutex_init(&_mx,0);
        getDate(&_lastArchiveDate);
#endif
    }

    ~Logger()
    {
        if (!_path.empty()) _f.close();
#ifdef _WIN32
        DeleteCriticalSection(&_mx);
#else
        pthread_mutex_destroy(&_mx);
        //free(_lastArchiveDate);
#endif
    }

    // cout will be used before this method is called
    // return true if init succeeded
    // the actual log file will be suffixed with .log and .log.YYYYMMDD
    bool init_(LPCTSTR pathPrefix, LogLevel loglevel)
    {
		_loglevel = loglevel;

        _path = pathPrefix;
#ifdef _WIN32
        _path += _T(".log");
#else
        _path += ".log";
#endif
        _f.open(_path.c_str(), std::ios_base::out | std::ios_base::app);
        _f << std::hex;
        return _f.good();
    }

    tostream & debug()
    {
        return log() << "G ";
    }
    tostream & error()
    {
        return log() << "R ";
    }

    tostream & info()
    {
        return log() << "O ";
    }

    tostream & warn()
    {
        return log() << "N ";
    }

    tostream & fatal()
    {
        return log() << "L ";
    }

	LogLevel loglevel()
	{
		return _loglevel;
	}
};

inline void operator <<(tostream & s, Logger & l)
{
    l.end(s);
}

#endif // SWIG

#endif
