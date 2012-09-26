// This file contains helper functions for string manipulation

#include <string>
#include <algorithm>

// Convert between string and wstring
// We need these because protobuf only supports string
inline std::wstring to_wstring(const std::string& s)
{
    std::wstring result(s.begin(), s.end());
    result.assign(s.begin(), s.end());
    return result;
}

inline std::string to_string(const std::wstring& ws)
{
    std::string result(ws.begin(), ws.end());
    result.assign(ws.begin(), ws.end());
    return result;
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
