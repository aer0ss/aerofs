import commands

def get_current_version():
    """
    Return the current AeroFS version as a string (Note that enterprise-greeter
    duplicates this function)
    """
    # The command finds the line "Version=0.1.2" and return the string after '='
    status, output = commands.getstatusoutput(
        'grep Version /opt/repackaging/installers/original/current.ver | cut -d = -f 2')
    return output if status == 0 else '(unknown)'