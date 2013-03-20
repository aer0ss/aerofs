# TODO: Reduce the number of imports needed here if possible
import platform
import imp
import os
import sys

# dynamically load libScryptPy depending on platform (Linux or OS X)
searchDir = ''
if platform.system() == 'Linux':
    # Check if we're running 32 or 64 bit python
    if sys.maxsize > 2**32:
        searchDir = 'lib/linux64'
    else:
        searchDir = 'lib/linux32'
else: # OS X (don't need to worry about other cases because they will fail when attempting import below)
    searchDir = 'lib/osx'
cwd = os.getcwd()
currentDir = os.path.dirname(__file__)
libDir = os.path.join(currentDir, searchDir)
os.chdir(libDir) # cd into directory containing library so dlopen can find dependencies
fl, pathname, description = imp.find_module('libScryptPy', [os.getcwd()])
libScryptPy = imp.load_module('libScryptPy', fl, pathname, description)
os.chdir(cwd)

# Our own copy of the function so scrypt.scrypt(args) can be called
def scrypt(password, email_address):
    # Remember to normalize the email address before scrypting.
    return libScryptPy.scrypt(password, email_address.lower())
