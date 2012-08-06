call restart_explorer.bat

devenv ..\AeroFSShellExtension.sln /Clean "Release|Win32"
devenv ..\AeroFSShellExtension.sln /Clean "Release|x64"
devenv ..\AeroFSShellExtension.sln /Build "Release|Win32"
devenv ..\AeroFSShellExtension.sln /Build "Release|x64"

copy ..\build\AeroFSShellExt32.dll ..\..\..\..\as-is\win\
copy ..\build\AeroFSShellExt64.dll ..\..\..\..\as-is\win\