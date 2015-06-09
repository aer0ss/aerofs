from flask import Flask
from subprocess import check_call
from os import unlink
from traceback import print_exc
from os.path import exists
from os import listdir
from aerofs_common.configuration import Configuration

NTP_CONF = '/etc/systemd/timesyncd.conf.d/ntp.conf'

app = Flask(__name__)


@app.route("/", methods=["POST"])
def post():
    reload_ntp()
    return ''

@app.errorhandler(500)
def internal_error(error):
    print_exc(error)
    return 'An internal server error has occurred. Check logs for more info.', 500


def reload_ntp():
    server = Configuration('http://config.service:5434', service_name='ntp').server_properties()['ntp.server']

    print "Setting NTP server to '{}'...".format(server)
    if server:
        with open(NTP_CONF, 'w') as f:
            f.write('[Time]\nNTP={}'.format(server))
    else:
        # Use the fallback NTP server list defined in run.sh
        if exists(NTP_CONF):
            unlink(NTP_CONF)

    print "Stopping NTP client..."
    call(['timedatectl', 'set-ntp', 'false'])

    if server:
        # Perform a possibly-large time adjustment, since set-ntp will only skew the clock, rather than forcing it to
        # skip. "ntpdate" has been deprecate for "sntp" but the latter is not available in Debian.
        # https://support.ntp.org/bin/view/Dev/DeprecatingNtpdate).
        print "Skipping clock..."
        call(['ntpdate', '-b', server])

    print "Starting NTP client..."
    call(['timedatectl', 'set-ntp', 'true'])


def call(cmd):
    if not listdir('/var/run/dbus'):
        print "WARN: /var/run/dbus is empty. The host may not be CoreOS. Skip command to help testing: {}".format(cmd)
    else:
        check_call(cmd)


reload_ntp()

print '''
API Usage:

    POST /  Configure the host to use the server specified by the config server's ntp.server property. If the property
            is empty, CoreOS's default NTP server pool will be used. The call also performs a possibly large time
            adjustment.

'''

app.run('0.0.0.0', 80)
