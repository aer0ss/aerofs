from pyramid.view import view_config
import re

_URL_PARAM_OS = 'os'

@view_config(
    route_name='download',
    renderer='download.mako',
    permission='user',
)
def download(request):
    return _download(request, False)

@view_config(
    route_name='download_team_server',
    renderer='download.mako',
    permission='admin',
)
def download_team_server(request):
    return _download(request, True)

def _download(request, is_team_server):
    return {
        'url_param_os': _URL_PARAM_OS,
        'is_team_server': is_team_server,
        'os': _get_browser_os(request)
    }


_REGEX_OSX = re.compile('.*(Mac_PowerPC|Macintosh|Darwin).*')
_REGEX_WIN = re.compile('.*(Windows|Win16|Win95|Windows_95|Win98|WinNT).*')
_REGEX_LINUX = re.compile('.*(OpenBSD|FreeBSD|SunOS|Linux|X11).*')

def _get_browser_os(request):
    if _REGEX_WIN.match(request.user_agent): return 'win'
    if _REGEX_OSX.match(request.user_agent): return 'osx'
    if _REGEX_LINUX.match(request.user_agent): return 'linux'
    else: return 'win'

@view_config(
    route_name='downloading',
    renderer='downloading.mako',
    permission='user',
)
def downloading(request):
    return _downloading(request, 'AeroFS',
        'AeroFSInstall.exe',
        'AeroFSInstall.dmg',
        'aerofs-installer.deb',
        'aerofs-installer.tgz',
        'aerofs-cli',
        'aerofs-sh')

@view_config(
    route_name='downloading_team_server',
    renderer='downloading.mako',
    permission='admin',
)
def downloading_team_server(request):
    return _downloading(request, 'AeroFS Team Server',
        'AeroFSTeamServerInstall.exe',
        'AeroFSTeamServerInstall.dmg',
        'aerofsts-installer.deb',
        'aerofsts-installer.tgz',
        'aerofsts-cli',
        'aerofsts-sh')

def _downloading(request, program, exe, dmg, deb, tgz, cli, sh):
    os = request.params.get('os')
    return {
        'os': os if os else _get_browser_os(request),
        'program': program,
        'exe': exe,
        'dmg': dmg,
        'deb': deb,
        'tgz': tgz,
        'cli': cli,
        'sh': sh
    }