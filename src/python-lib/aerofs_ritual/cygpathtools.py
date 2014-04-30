from os.path import normpath, expanduser


def winpath_to_cygpath(wp):
    """ Convert 'c:\path\to\file' to '/cygdrive/c/path/to/file' """
    wp = normpath(wp)
    if len(wp) > 1 and wp[1] == ':':
        # Handle absolute paths
        return '/'.join(['/cygdrive', wp[0].lower()] + wp.replace('\\','/').split('/')[1:])
    else:
        # Handle relative paths
        return wp.replace('\\', '/')


def cygpath_to_winpath(cp):
    """
    Convert '/cygdrive/c/path/to/file' to 'c:\path\to\file'
    Convert '/home/path/to/file' to 'c:\cygwin\home\path\to\file'

    N.B. this assumes that cygwin is installed at c:\cygwin !!!
    """
    cp = normpath(expanduser(cp))
    if cp[0] == '/':
        # Handle absolute path
        split = cp.split('/')[1:]
        if split[0] == 'cygdrive':
            return '/'.join(['{}:'.format(split[1])] + split[2:])
        else:
            return '/'.join(['c:', 'cygwin'] + split)
    else:
        # Handle relative paths
        return cp