import os
import shutil
import socket
import datetime
from subprocess import call, Popen, PIPE
import tempfile

from aerofs_common.configuration import Configuration

def _flag_file_folder(settings):
    return settings.get('deployment.flag_file_folder', "/var/aerofs")

def is_configuration_initialized(settings):
    basepath = _flag_file_folder(settings)
    return os.path.exists(os.path.join(basepath, 'configuration-initialized-flag'))

def is_maintenance_mode(settings):
    # bootstrap tasks maintenance-enter & maintenance-exit create and delete
    # this file
    basepath = _flag_file_folder(settings)
    return os.path.exists(os.path.join(basepath, 'maintenance-flag'))

def has_external_db(settings):
    basepath = _flag_file_folder(settings)
    return os.path.exists(os.path.join(basepath, 'external-db-flag'))

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
    new_hours = local_time.replace(hour = ((local_time.hour + offset) % 24))
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


def is_hostname_resolvable(hostname):
    try:
        socket.gethostbyname(hostname)
        return True
    except socket.error:
        return False

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
