import os
import shutil
import socket
import datetime
from subprocess import call, Popen, PIPE
import tempfile
import requests

from aerofs_common.configuration import Configuration
from aerofs_common.constants import CONFIG_COMPLETED_FLAG_FILE

_EXTERNAL_DB_FLAG_FILE = '/data/bunker/external-db-flag'
_CONFIG_STARTED_FLAG_FILE='/data/bunker/configuration-started-flag'

def is_configuration_completed():
    return os.path.exists(CONFIG_COMPLETED_FLAG_FILE)

def is_configuration_started():
    return os.path.exists(_CONFIG_STARTED_FLAG_FILE)

def set_configuration_completed():
    open(CONFIG_COMPLETED_FLAG_FILE, 'w').close()

def set_configuration_started():
    open(_CONFIG_STARTED_FLAG_FILE, 'w').close()


def has_external_db(settings):
    return os.path.exists(_EXTERNAL_DB_FLAG_FILE)


_maintenance_mode_cache = None


def is_maintenance_mode(settings):
    """
    The system is in maintenance mode if the current target is "maintenance" and the system
    has been initialized.
    """
    global _maintenance_mode_cache
    if _maintenance_mode_cache is None:
        if is_configuration_completed():
            r = requests.get('http://loader.service/v1/boot')
            r.raise_for_status()
            _maintenance_mode_cache = r.json()['target'] == 'maintenance'
        else:
            _maintenance_mode_cache = False
    return _maintenance_mode_cache


def write_pem_to_file(pem_string):
    os_handle, filename = tempfile.mkstemp()
    os.write(os_handle, pem_string.encode('utf8'))
    os.close(os_handle)
    return filename


def is_certificate_formatted_correctly(certificate_filename):
    return call(["/usr/bin/openssl", "x509", "-in", certificate_filename,
                 "-noout"]) == 0


def is_key_formatted_correctly(key_filename):
    return call(["/usr/bin/openssl", "rsa", "-in", key_filename, "-noout"]) == 0


def get_modulus_of_certificate_file(certificate_filename):
    """
    Expects as input a filename pointing to a valid PEM certtificate file.
    """
    cmd = ["/usr/bin/openssl", "x509", "-noout", "-modulus", "-in",
           certificate_filename]
    return _get_modulus_helper(cmd)


def get_modulus_of_key_file(key_filename):
    """
    Expects as input a filename pointing to a valid PEM key file.
    """
    cmd = ["/usr/bin/openssl", "rsa", "-noout", "-modulus", "-in", key_filename]
    return _get_modulus_helper(cmd)


def format_pem(string):
    """
    Format certificate and key using this function before saving to the
    configuration service.
    """
    return string.strip().replace('\n', '\\n').replace('\r', '')


def unformat_pem(string):
    """
    Convert the formatted pem string back to HTML format.
    """
    return string.replace('\\n', '\n')


def format_time(form_value, offset):
    """
    Convert a time of the form 1:15 PM to the form 13:15, as well as
    adding 'offset' number of hours - usually to convert to UTC
    """
    local_time = datetime.datetime.strptime(form_value, "%I:%M %p").time()
    new_hours = local_time.replace(hour=((local_time.hour + offset) % 24))
    return new_hours.strftime("%H:%M")


def _get_modulus_helper(cmd):
    p = Popen(cmd, stdout=PIPE, stderr=PIPE)
    stdout, stderr = p.communicate()
    return stdout.split('=')[1]


def get_conf(request):
    return get_conf_client(request).server_properties()


def get_conf_client(request):
    return Configuration(request.registry.settings["deployment.config_server_uri"], service_name='bunker')


def is_ipv4_address(string):
    try:
        socket.inet_aton(string)
        return True
    except socket.error:
        return False

def is_ipv6_address(string):
    try:
        socket.inet_pton(socket.AF_INET6, string)
        return True
    except socket.error:
        return False

def is_hostname_resolvable(hostname):
    try:
        socket.gethostbyname(hostname)
        return True
    except socket.error:
        return False


def is_hostname_xmpp_compatible(hostname):
    """
    Similar code in function 'getServerDomain()',
    make sure any changes to this code block are reflected accordingly in BaseParam.java
    """
    if len(hostname.split(".")) >= 2:
        return True


def save_file_to_path(file, path):
    directory, filename = os.path.split(path)
    if not os.path.exists(directory):
        os.makedirs(directory)

    # Clean up old file
    if os.path.exists(path):
        os.remove(path)

    file.seek(0)
    with tempfile.NamedTemporaryFile(dir=directory, prefix=filename, delete=False) as tmp_file:
        shutil.copyfileobj(file, tmp_file)
        os.rename(tmp_file.name, path)
