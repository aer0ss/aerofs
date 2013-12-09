#!/usr/bin/env python
# A python script to retrieve log files and retrace them.


import os
import re
import shutil
import subprocess
import tempfile
import zipfile

from argparse import ArgumentParser, RawTextHelpFormatter


_C_HOSTNAME         = 'c.aerofs.com'
_DEFECT_FOLDER_NAME = 'defect-{0}'
_LOG_NAMES          = ['cli.log', 'daemon.log', 'gui.log']
_MAP_FILE_NAME      = 'aerofs-{0}-public.map'
_NEW_VERSION_SUFFIX = '========================================================\n'
_OBFUSCATED_PREFIX  = 'obfuscated.'
_PROJ_PREFIX        = 'com.aerofs.'
_SCRIPT_DIR           = os.path.dirname(os.path.realpath(__file__))
_SCRIPT_NAME          = os.path.basename(os.path.realpath(__file__))
_SV_HOSTNAME        = 'sv.aerofs.com'
_ZIP_NAME           = 'log.defect-{0}.zip'

_COM_AEROFS_REGEX     = re.compile(_PROJ_PREFIX + '(\w+)')
_DEFAULT_RETRACE_PATH = os.path.join(_SCRIPT_DIR, 'proguard', 'bin', 'retrace.sh')
_VERSION_REGEX        = re.compile('\d+\.\d+\.\d+')


def get_defects(defects):
    """
    @param defects: A list of defects to retrieve and retrace.
    """
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
        retrace_logs(logs, defect)
        print '[{0}] Done.'.format(_SCRIPT_NAME)


def get_map(directory, version):
    """
    @param directory : Directory in which to put the map.
    @param version   : AeroFS version of the retrace map to retrieve.
    """
    map_file_name = _MAP_FILE_NAME.format(version)
    map_path      = os.path.join(directory, map_file_name)

    if not os.path.isfile(map_path):
        print '[{0}] Retrieving version {1} map '.format(_SCRIPT_NAME, version)
        remote_map_path = _SV_HOSTNAME + ':/maps/' + map_file_name
        subprocess.check_call(['scp', remote_map_path, map_path])

    return map_path


def retrace(log_file_path, defect=None, maps_folder=_SCRIPT_DIR):
    """
    Retraces a log file. Retraces each section with the appropriate version,
    leaving the initial (unmarked) section unchanged.

    @param maps_folder   : The folder in which to store maps.
    @param log_file_path : Path to the log file to retrace
    """
    file_paths, versions = _split_log_by_version(log_file_path)
    num_subfiles = len(file_paths)

    # Retrace each subfile appropriately, detecting the case where we have only
    # one subfile, and hence want to use sv to determine version information.
    first_path = log_file_path + '.1'
    if num_subfiles is 1 and defect is not None:
        versions[0] = _get_version_from_sv(defect)

    for path, version in zip(file_paths, versions):
        # If we couldn't determine version, don't retrace.
        if version is None:
            obfuscated_first_path = _obfuscate_path(first_path)
            shutil.copyfile(first_path, obfuscated_first_path)
            continue

        map_path = get_map(maps_folder, version)
        print '[{0}] Retrace part of {1} with version {2}'.format(_SCRIPT_NAME,
                log_file_path, version)
        retrace_with_map(path, map_path)

    # Merge the subfiles back together.
    obfuscated_file_paths = [_obfuscate_path(path) for path in file_paths]
    _merge_files(file_paths, log_file_path)
    _merge_files(obfuscated_file_paths, _obfuscate_path(log_file_path))


def retrace_logs(logs, defect=None):
    """
    @param logs: List of paths to log files to retrace.
    @param defect: Defect that these logs correspond to.
    """
    for log in logs:
        if os.path.isfile(log):
            print '[{0}] Retrace {1}'.format(_SCRIPT_NAME, log)
            retrace(log, defect)
        else:
            print '[{0}] {1} is not a file, ignoring.'.format(_SCRIPT_NAME, log)


def retrace_with_map(log_file_path, map_path):
    """
    Deobfuscate most of the obfuscated java in log_file_path.

    @param log_file_path : Path of the log file to deobfuscate.
    @param map_path      : Path to the map file to use for deobfuscation.
    @param retrace_path  : Path to proguard retrace script.
    """
    clean_log_path      = log_file_path
    obfuscated_log_path = _obfuscate_path(log_file_path)
    os.rename(clean_log_path, obfuscated_log_path)

    _, tmpfile = tempfile.mkstemp()
    _manual_retrace(obfuscated_log_path, tmpfile, map_path)

    subprocess.check_call([_DEFAULT_RETRACE_PATH,
        map_path,
        tmpfile],
        stdout=open(clean_log_path, 'w'))

    os.remove(tmpfile)


def _get_version_from_line(line):
    """
    @param  : The line from which to extract the version number.
    @return : The version (ie 0.4.205) number from the beginning of line.
    """
    version = line.split(' ', 1)[0]
    if not _VERSION_REGEX.match(version): return None
    else                                : return version


def _get_version_from_sv(defect):
    version = subprocess.check_output(
            ['ssh', _C_HOSTNAME, 'getver {}'.format(defect)])
    return version.strip()


def _obfuscate_path(path):
    """
    Returns path, but with the filename prepended with _OBFUSCATED_PREFIX.

    @param path : Path to the file whose name should be prepended.
    """
    name   = os.path.basename(path)
    folder = os.path.dirname(path)
    return os.path.join(folder, _OBFUSCATED_PREFIX + name)


def _merge_files(paths, out_path):
    """
    `cat paths > out_path; rm paths`.
    """
    with open(out_path, 'w') as out_file:
        for path in paths:
            with open(path) as in_file:
                for line in in_file:
                    out_file.write(line)
            os.remove(path)


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


def _split_log_by_version(log_file_path):
    """
    Split log file into subfiles based on version.

    @param log_file_path: The path to the log file to split.
    """
    print '[{0}] Splitting {1} into version subfiles.'.format(_SCRIPT_NAME,
            log_file_path)

    num_subfiles = 1
    first_path = log_file_path + '.' + str(num_subfiles)
    file_paths   = [first_path]
    f_out        = open(first_path, 'w')
    versions     = [None]

    log_file = open(log_file_path)
    line = log_file.readline()
    while line:
        if line.endswith(_NEW_VERSION_SUFFIX):
            # Immediately move to next line after seeing suffix marker.
            f_out.write(line)
            line = log_file.readline()
            if not line: break

            version = _get_version_from_line(line)

            # version is None iff there is not valid version in line.
            if version:
                f_out.close()
                num_subfiles   += 1
                next_file_path  = log_file_path + '.' + str(num_subfiles)
                f_out           = open(next_file_path, 'w')

                file_paths.append(next_file_path)
                versions.append(version)

        f_out.write(line)
        line = log_file.readline()
    f_out.close()

    return file_paths, versions


if __name__ == '__main__':
    parser = ArgumentParser(formatter_class=RawTextHelpFormatter)
    parser.add_argument('command',
            help='[ get-defects | gd | retrace-logs | rl ]')
    parser.add_argument('args', nargs='*',
            help=('For get-defects: List of defects to get.\n'
                'For retrace-logs: List of logs to retrace.'))
    args = parser.parse_args()

    command = args.command.lower()
    if   command == 'get-defects'  or command == 'gd':
        get_defects(args.args)
    elif command == 'retrace-logs' or command == 'rl':
        retrace_logs(args.args)
    else:
        parser.print_help()
