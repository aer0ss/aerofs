// This file contains helper functions for string manipulation

#include <string>
#include <algorithm>
#include <windows.h>
#include <sstream>

// Convert between string and wstring
// We need these because protobuf only supports string
// and uses UTF-8 as its encoding, but Windows only
// supports Unicode in paths with UTF-16
inline std::wstring widen(const std::string& utf8s)
{
    std::wstring utf16s(L"");
    int size_needed = MultiByteToWideChar(CP_UTF8, // Codepage
            0,                          // Flags
            utf8s.c_str(),              // UTF8 string data
            -1,                         // # of characters, or -1 if null-terminated
            NULL,                       // outarg UTF16 data (null to query size)
            0);                         // characters available at UTF16 data pointer
    if (size_needed == 0) {
        // Something went wrong.
        return utf16s;
    }
    wchar_t* buffer = new wchar_t[size_needed];
    int size = MultiByteToWideChar(CP_UTF8, // Codepage
            0,                          // Flags
            utf8s.c_str(),              // UTF8 string data
            -1,                         // # of characters, or -1 if null-terminated
            buffer,                     // outarg UTF16 data
            size_needed);               // characters available at UTF16 data pointer
    utf16s.assign(buffer);
    delete [] buffer;
    return utf16s;
}

inline std::string narrow(const std::wstring& utf16s)
{
    std::string utf8s("");
    int size_needed = WideCharToMultiByte(CP_UTF8, // Codepage
            0,                          // Flags
            utf16s.c_str(),             // UTF16 string data
            -1,                         // # of characters, or -1 if null-terminated
            NULL,                       // outarg UTF8 data
            0,                          // bytes available at UTF8 data pointer
            NULL,                       // default char (must be NULL)
            NULL);                      // default char used (must be NULL)
    if (size_needed == 0) {
        // Something went wrong.
        return utf8s;
    }
    char* buffer = new char[size_needed];
    int size = WideCharToMultiByte(CP_UTF8, // Codepage
            0,                          // Flags
            utf16s.c_str(),             // UTF16 string data
            -1,                         // # of characters, or -1 if null-terminated
            buffer,                     // outarg UTF8 data
            size_needed,                // bytes available at UTF8 data pointer
            NULL,                       // default char (must be NULL)
            NULL);                      // default char used (must be NULL)
    utf8s.assign(buffer);
    delete [] buffer;
    return utf8s;
}

inline std::wstring lowercase(const std::wstring& s)
{
    std::wstring str = s;
    std::transform(str.begin(), str.end(), str.begin(), ::tolower);
    return str;
}

/**
    * splits a string according to a separator
    * Empty parts are removed
    */
inline std::vector<std::wstring> split(const std::wstring& s, wchar_t sep)
{
    std::vector<std::wstring> tokens;
    size_t start = 0, end = 0;
    while ((end = s.find(sep, start)) != std::string::npos) {
	    std::wstring tok = s.substr(start, end - start);
	    if (!tok.empty()) {
		    tokens.push_back(tok);
	    }
	    start = end + 1;
    }

    std::wstring tok = s.substr(start);
    if (!tok.empty()) {
	    tokens.push_back(tok);
    }
    return tokens;
}

/**
    * return true if str starts with prefix
    */
inline bool str_starts_with(const std::wstring& str, const std::wstring& prefix)
{
    return (str.compare(0, prefix.length(), prefix) == 0);
}

/**
    * return true if str ends with suffix
    */
inline bool str_ends_with(const std::wstring& str, const std::wstring& suffix)
{
    if (str.length() >= suffix.length()) {
        return (str.compare(str.length() - suffix.length(), suffix.length(), suffix) == 0);
    } else {
        return false;
    }
}

/**
  * Encodes the bytes in a string as their hex equivalents.  Useful for debugging
  * issues with encodings and paths.
  */
inline std::string hexencode(std::string arg) {
	std::stringstream ss;
	for (size_t i = 0; i < arg.size(); i++) {
		ss << std::hex << (unsigned int)((unsigned char)arg[i]);
	}
	return ss.str();
}
