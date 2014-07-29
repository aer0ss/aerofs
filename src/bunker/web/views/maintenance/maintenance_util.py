import os
import socket
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
    basepath = _flag_file_folder(settings);
    return os.path.exists(os.path.join(basepath, 'maintenance-flag'))

def write_pem_to_file(pem_string):
    os_handle, filename = tempfile.mkstemp()
    os.write(os_handle, pem_string)
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
    configuration service. See also the code in identity.mako that convert
    the string to HTML format.
    """
    return string.strip().replace('\n', '\\n').replace('\r', '')


def _get_modulus_helper(cmd):
    p = Popen(cmd, stdout=PIPE, stderr=PIPE)
    stdout, stderr = p.communicate()
    return stdout.split('=')[1]


def get_conf(request):
    return get_conf_client(request).server_properties()


def get_conf_client(request):
    return Configuration(request.registry.settings["deployment.config_server_uri"])


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
