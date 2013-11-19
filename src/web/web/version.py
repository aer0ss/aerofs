import commands

_version = None

def get_current_version():
    """
    Return the current AeroFS version as a string (Note that enterprise-greeter
    duplicates this function)
    """
    global _version
    # cache the version to avoid frequent shell-outs
    if not _version:
        # The command finds the line "Version=0.1.2" and return the string after '='
        status, output = commands.getstatusoutput(
            'grep Version /opt/repackaging/installers/original/current.ver | cut -d = -f 2')
        _version = output if status == 0 else '(unknown)'

    return _version