#include "util.h"

std::string stripExtension(const std::string& fileName)
{
    std::string::size_type lastPeriod = fileName.find_last_of('.');
    return fileName.substr(0, lastPeriod);
}

std::string getBaseFilename(const std::string& fileName)
{
    std::string::size_type lastSlash = fileName.find_last_of('/');
    if (lastSlash == fileName.npos) {
        return fileName;
    }
    return fileName.substr(lastSlash + 1);
}
