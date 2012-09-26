#pragma once

/**
Very simple logging framework

Uses the Windows' OutputDebugString()
Debug messages can be viewed with a tool such as DebugView:
http://technet.microsoft.com/en-us/sysinternals/bb896647

Example:
	int someInteger = 42;
	wchar_t* someCString = L"hello";
	DEBUG_LOG("The integer is " << someInteger << " and the string is " << std::wstring(someCString));

Use DEBUG_LOG for debugging messages. Those won't be compiled in Release mode.
Use INFO_LOG for information messages and warnings that should go into Release.
Use ERROR_LOG for important errormessages that should go into Release.
*/

#include <sstream>

inline void logW(const std::wostringstream& stream)
{
	OutputDebugStringW(stream.str().c_str());
}

#ifdef _DEBUG
  #define DEBUG_LOG(msg) { std::wostringstream buf; buf << msg; logW(buf); }
#else
  #define DEBUG_LOG(msg)
#endif
#define ERROR_LOG(msg) { std::wostringstream buf; buf << "AeroFS ERROR: " << msg; logW(buf); }
#define  INFO_LOG(msg) { std::wostringstream buf; buf << "AeroFS: " << msg; logW(buf); }
