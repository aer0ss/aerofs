# A python script to retrace logs.

import sys
import os
import re
import shutil
import subprocess
import tempfile

MAP_FILE_NAME      = 'aerofs-{0}-public.map'
NEW_VERSION_SUFFIX = '========================================================\n'
OBFUSCATED_PREFIX  = 'obfuscated.'
PROJ_PREFIX        = 'com.aerofs.'
SCRIPT_DIR         = os.path.dirname(os.path.realpath(__file__))
SCRIPT_NAME        = os.path.basename(os.path.realpath(__file__))
MAP_HOSTNAME       = 'rocklog.aerofs.com'

LOGLINE_REGEX        = re.compile("([a-z,A-Z]* \[.*\] \[.*\] )([a-z,A-Z]*)(:.*)")
COM_AEROFS_REGEX     = re.compile(PROJ_PREFIX + '(\w+)')
DEFAULT_RETRACE_PATH = os.path.join(SCRIPT_DIR, 'proguard', 'bin', 'retrace.sh')
VERSION_REGEX        = re.compile('\d+\.\d+\.\d+')

def get_map(directory, version):
    """
    @param directory : Directory in which to put the map.
    @param version   : AeroFS version of the retrace map to retrieve.
    """
    map_file_name = MAP_FILE_NAME.format(version)
    map_path      = os.path.join(directory, map_file_name)

    if not os.path.isfile(map_path):
        print '[{0}] Retrieving version {1} map '.format(SCRIPT_NAME, version)
        remote_map_path = MAP_HOSTNAME + ':/maps/' + map_file_name
        subprocess.check_call(['scp', remote_map_path, map_path])

    return map_path

def retrace_log(log_file_path, maps_folder=SCRIPT_DIR):
    """
    Retraces a log file. Retraces each section with the appropriate version, leaving the initial
    (unmarked) section unchanged.

    @param maps_folder   : The folder in which to store maps.
    @param log_file_path : Path to the log file to retrace
    """
    file_paths, versions = split_log_by_version(log_file_path)

    # Retrace each subfile appropriately, detecting the case where we have only one subfile, and
    # hence want to use sv to determine version information.
    first_path = log_file_path + '.1'

    for path, version in zip(file_paths, versions):
        # If we couldn't determine version, don't retrace.
        if version is None:
            obfuscated_first_path = obfuscate_path(first_path)
            shutil.copyfile(first_path, obfuscated_first_path)
            continue

        map_path = get_map(maps_folder, version)
        print '[{0}] Retrace part of {1} with version {2}'.format(SCRIPT_NAME, log_file_path,
                                                                  version)
        retrace_with_map(path, map_path)

    # Merge the subfiles back together.
    obfuscated_file_paths = [obfuscate_path(path) for path in file_paths]
    merge_files(file_paths, log_file_path)
    merge_files(obfuscated_file_paths, obfuscate_path(log_file_path))

def retrace_with_map(log_file_path, map_path):
    """
    Deobfuscate most of the obfuscated java in log_file_path.

    @param log_file_path : Path of the log file to deobfuscate.
    @param map_path      : Path to the map file to use for deobfuscation.
    """
    clean_log_path      = log_file_path
    obfuscated_log_path = obfuscate_path(log_file_path)
    os.rename(clean_log_path, obfuscated_log_path)

    _, tmpfile = tempfile.mkstemp()
    manual_retrace(obfuscated_log_path, tmpfile, map_path)

    subprocess.check_call([DEFAULT_RETRACE_PATH,
        map_path,
        tmpfile],
        stdout=open(clean_log_path, 'w'))

    os.remove(tmpfile)

def get_version_from_line(line):
    """
    @param  : The line from which to extract the version number.
    @return : The version (ie 0.4.205) number from the beginning of line.
    """
    version = line.split(' ', 1)[0]
    if not VERSION_REGEX.match(version): return None
    else                                : return version

def obfuscate_path(path):
    """
    Returns path, but with the filename prepended with _OBFUSCATED_PREFIX.

    @param path : Path to the file whose name should be prepended.
    """
    name   = os.path.basename(path)
    folder = os.path.dirname(path)
    return os.path.join(folder, OBFUSCATED_PREFIX + name)

def merge_files(paths, out_path):
    """
    `cat paths > out_path; rm paths`.
    """
    with open(out_path, 'w') as out_file:
        for path in paths:
            with open(path) as in_file:
                for line in in_file:
                    out_file.write(line)
            os.remove(path)

def manual_retrace(log_path, out_log_path, map_path):
    """
    Deobfuscate lines in the file at log_path that look like `.*@<class>,.*`.

    @param log_path     : Path to file to partially deobfuscate.
    @param out_log_path : Path to write partially deobfuscated file to.
    @param map_path     : Path to map file used in deobfuscation.
    """
    assert log_path != out_log_path

    # Obtain the retrace map from the map file.
    retrace_map = {}
    for line in open(map_path, 'r'):
        if line.startswith('c'): # All non class lines start with whitespace.
            class_name, obfuscated_class_name = line.split(' -> ')
            key = obfuscated_class_name[len(PROJ_PREFIX):]
            key = key.strip(' :\n')
            retrace_map[key] = class_name

    out_file = open(out_log_path, 'w')
    for initial_line in open(log_path, 'r'):
        # Find/replace all com.aerofs.XX strings with their class.
        line = ''
        last_match_end = 0
        match = COM_AEROFS_REGEX.search(initial_line)
        while match:
            curr_match_begin = last_match_end + match.span()[0]
            line += initial_line[last_match_end:curr_match_begin]
            line += retrace_map.get(match.group(1), PROJ_PREFIX + match.group(1))
            last_match_end += match.span()[1]
            match = COM_AEROFS_REGEX.search(initial_line[last_match_end:])
        line += initial_line[last_match_end:]

        # Using knowledge of our log line format, find the key we wish to retrace and replace it.
        match = LOGLINE_REGEX.search(line)
        if match is not None:
            prefix = match.group(1)
            key    = match.group(2)
            info   = match.group(3)
            out_file.write((prefix + retrace_map.get(key, key) + info).rstrip() + '\n')
        else:
            out_file.write(line.rstrip() + '\n')

def split_log_by_version(log_file_path):
    """
    Split log file into subfiles based on version.

    @param log_file_path: The path to the log file to split.
    """
    print '[{0}] Splitting {1} into version subfiles.'.format(SCRIPT_NAME, log_file_path)

    num_subfiles = 1
    first_path = log_file_path + '.' + str(num_subfiles)
    file_paths   = [first_path]
    f_out        = open(first_path, 'w')
    versions     = [None]

    log_file = open(log_file_path)
    line = log_file.readline()
    while line:
        if line.endswith(NEW_VERSION_SUFFIX):
            # Immediately move to next line after seeing suffix marker.
            f_out.write(line)
            line = log_file.readline()
            if not line: break

            version = get_version_from_line(line)

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
    if len(sys.argv) != 2:
        print("Usage: %s <log_file_path>" % sys.argv[0])
        sys.exit(1)

    retrace_log(sys.argv[1])
