from subprocess import call
import os
import sys

def make():
    os.chdir(sys.argv[1])
    call(['make', '-j8'])


if __name__ == '__main__':
    make()