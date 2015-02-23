from lib.app import aerofs_proc
from syncdet.case.assertion import assertEqual


# Note that this system test fails on Windows because we use a particular
# version of JLine 2.0, and that version does not work on Windows.
def main():
    output = aerofs_proc.run_sh_and_check_output('-e', 'help')

    assertEqual(
        'AeroFS> help',
        output.splitlines()[0].strip()
    )


spec = {'entries': [main]}
