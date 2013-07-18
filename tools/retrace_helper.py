#!/usr/bin/env python
# A python script to retrieve log files and retrace them.


import argparse
import os
import re
import subprocess
import tempfile
import zipfile


_DEFECT_FOLDER_NAME = 'defect-{0}'
_LOG_NAMES          = ['cli.log', 'daemon.log', 'gui.log']
_MAP_FILE_NAME      = 'aerofs-{0}-prod.map'
_PROJ_PREFIX        = 'com.aerofs.'
_SV_HOSTNAME        = 'sv.aerofs.com'
_ZIP_NAME           = 'log.defect-{0}.zip'

_COM_AEROFS_REGEX     = re.compile(_PROJ_PREFIX + '(\w+)')
_SCRIPT_DIR           = os.path.dirname(os.path.realpath(__file__))
_SCRIPT_NAME          = os.path.basename(os.path.realpath(__file__))
_DEFAULT_RETRACE_PATH = os.path.join(_SCRIPT_DIR, 'proguard', 'bin', 'retrace.sh')


def get_defects(version, defects):
    """
    @param version: The AeroFS version number whose map file we should use.
    @param defects: A list of defects to retrieve and retrace.
    """
    print '[{0}] Retrieving map file...'.format(_SCRIPT_NAME)
    map_path = get_map(_SCRIPT_DIR, version)

    for defect in defects:
        defect_folder = _DEFECT_FOLDER_NAME.format(defect)
        try:
            os.mkdir(defect_folder)
        except OSError:
            print defect_folder + ' already exists.'
            continue

        zip_name = _ZIP_NAME.format(defect)
        remote_zip_path = _SV_HOSTNAME + ':/var/svlogs_prod/defect/' + zip_name
        zip_path = os.path.join(_SCRIPT_DIR, zip_name)
        print '[{0}] Retrieving zip file...'.format(_SCRIPT_NAME)
        subprocess.check_call(['scp', remote_zip_path, zip_path])

        print '[{0}] Unzipping file...'.format(_SCRIPT_NAME)
        zipfile.ZipFile(zip_path).extractall(defect_folder)
        os.remove(zip_name)

        print '[{0}] Retracing logs for {1}...'.format(_SCRIPT_NAME, defect)
        logs = [os.path.join(defect_folder, log_name) for log_name in _LOG_NAMES]
        retrace_logs(map_path, logs)
        print '[{0}] Done.'.format(_SCRIPT_NAME)


def get_map(directory, version):
    """
    @param directory : Directory in which to put the map.
    @param version   : AeroFS version of the retrace map to retrieve.
    """
    map_file_name = _MAP_FILE_NAME.format(version)
    map_path      = os.path.join(directory, map_file_name)

    if not os.path.isfile(map_path):
        remote_map_path = _SV_HOSTNAME + ':/maps/' + map_file_name
        subprocess.check_call(['scp', remote_map_path, map_path])

    return map_path


def retrace(log_file_path, map_path):
    """
    Deobfuscate most of the obfuscated java in log_file_path.

    @param log_file_path : Path of the log file to deobfuscate.
    @param map_path      : Path to the map file to use for deobfuscation.
    @param retrace_path  : Path to proguard retrace script.
    """
    log_file_name = os.path.basename(log_file_path)
    log_folder    = os.path.dirname(log_file_path)

    clean_log_path      = log_file_path
    obfuscated_log_path = os.path.join(log_folder, 'obfuscated.' + log_file_name)
    os.rename(clean_log_path, obfuscated_log_path)

    _, tmpfile = tempfile.mkstemp()
    _manual_retrace(obfuscated_log_path, tmpfile, map_path)

    subprocess.check_call([_DEFAULT_RETRACE_PATH,
        map_path,
        tmpfile],
        stdout=open(clean_log_path, 'w'))

    os.remove(tmpfile)


def retrace_logs(map_path, logs):
    """
    @param map_path: Path to map file for use during retrace.
    @param logs: List of paths to log files to retrace.
    """
    for log in logs:
        if os.path.isfile(log):
            print "retrace " + log + ", " + map_path
            retrace(log, map_path)
        else:
            print '[{0}] {1} is not a file, ignoring.'.format(_SCRIPT_NAME, log)


def _manual_retrace(log_path, out_log_path, map_path):
    """
    Deobfuscate lines in the file at log_path that look like `.*@<class>,.*`.

    @param log_path     : Path to file to partially deobfuscate.
    @param out_log_path : Path to write partially deobfuscated file to.
    @param map_path     : Path to map file used in deobfuscation.
    """
    assert log_path != out_log_path

    retrace_map = {}
    for line in open(map_path, 'r'):
        if line.startswith('c'): # All non class lines start with whitespace.
            class_name, obfuscated_class_name = line.split(' -> ')

            key = obfuscated_class_name[len(_PROJ_PREFIX):]
            key = key.strip(' :\n')

            retrace_map[key] = class_name

    out_file = open(out_log_path, 'w')
    for initial_line in open(log_path, 'r'):
        # Find/replace all com.aerofs.XX strings with their class.
        line = ''
        last_match_end = 0
        match = _COM_AEROFS_REGEX.search(initial_line)
        while match:
            curr_match_begin = last_match_end + match.span()[0]

            line += initial_line[last_match_end:curr_match_begin]
            line += retrace_map.get(match.group(1), _PROJ_PREFIX + match.group(1))

            last_match_end = last_match_end + match.span()[1]
            match = _COM_AEROFS_REGEX.search(initial_line[last_match_end:])
        line += initial_line[last_match_end:]

        # Replace the XX @<class>, XX with it's full class.
        if ',' in line:
            line_begin, line_end = line.split(',', 1)
            if '@' in line_begin:
                line_begin, key = line_begin.split('@', 1)

                line_begin += '@'
                line_end    = ',' + line_end

                out_file.write(line_begin + retrace_map.get(key, key) + line_end)

                # If substitution was successful, don't recopy the line.
                continue

        # If substitution was unsuccessful, just copy the line.
        out_file.write(line)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('command', help='get-defects (gd) or retrace-logs (rl)')
    parser.add_argument('arg', nargs='*')
    args = parser.parse_args()

    command = args.command.lower()
    if   command == 'get-defects'  or command == 'gd':
        get_defects(args.arg[0], args.arg[1:])
    elif command == 'retrace-logs' or command == 'rl':
        retrace_logs(args.arg[0], args.arg[1:])
    else:
        parser.print_help()
