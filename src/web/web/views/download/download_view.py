from pyramid.view import view_config
import re
from web.analytics import send_analytics_event
from web.version import get_private_version


@view_config(
    route_name='download',
    renderer='download.mako',
    permission='user',
)
def download(request):
    send_analytics_event(request, "ACTIVE_USER")
    return _download(request, False)


@view_config(
    route_name='download_team_server',
    renderer='download.mako',
    permission='admin',
)
def download_team_server(request):
    send_analytics_event(request, "ACTIVE_USER")
    return _download(request, True)


@view_config(
    route_name='download_sccm',
    renderer='download_sccm.mako',
    permission='admin',
)
def download_sccm(request):
    send_analytics_event(request, "ACTIVE_USER")
    path = 'AeroFSInstall-{}.msi'.format(get_private_version(request.registry.settings))
    return {
        'url':  request.static_url('web:installer/{}'.format(path))
    }


def _download(request, is_team_server):
    return {
        'msg_type': request.params.get('msg_type'),
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
        'AeroFSInstall',
        'AeroFSInstall',
        'aerofs-installer',
        'aerofs-installer',
        'aerofs-cli',
        'aerofs-sh',
        False)


@view_config(
    route_name='downloading_team_server',
    renderer='downloading.mako',
    permission='admin',
)
def downloading_team_server(request):
    return _downloading(request, 'AeroFS Team Server',
        'AeroFSTeamServerInstall',
        'AeroFSTeamServerInstall',
        'aerofsts-installer',
        'aerofsts-installer',
        'aerofsts-cli',
        'aerofsts-sh',
        True )


def _downloading(request, program, exe, dmg, deb, tgz, cli, sh, ts):
    os = request.params.get('os')
    msg_type = request.params.get('msg_type')
    version = get_private_version(request.registry.settings)
    return {
        'os': os if os else _get_browser_os(request),
        'program': program,
        'exe': '{}-{}.exe'.format(exe, version),
        'dmg': '{}-{}.dmg'.format(dmg, version),
        'deb': '{}-{}.deb'.format(deb, version),
        'tgz': '{}-{}.tgz'.format(tgz, version),
        'cli': cli,
        'sh': sh,
        'is_team_server': ts,
        'msg_type': msg_type
    }
