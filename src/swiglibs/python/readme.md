# Building libScryptPy

Now this is a story all about how my life got flipped, turned upside down,
And I'd like to take a minute just sit right there, I'll tell you how to build
libScryptPy.dll on cygwin.


## Overview

Authentication on SP requires the Scrypt library. In scrypt.py, os.find_module and
os.load_module are used to import from libScryptPy.dll or libScryptPy.so, depending
on your platform.

These libraries are expected to be in ./lib/[os] relative to scrypt.py.

## How to build on Cygwin
You must be on cygwin on Windows to build this DLL. Install Cygwin to C:\cygwin,
and ensure that it has Python 2.7 and openssl-dev (if I forgot a package, please edit this readme!)

This directory should contain Scrypt.c and a Makefile. Three other source files should be under
../src/scrypt/ relative to this directory.

Run "make"

This will compile the DLL and copy it to the proper folder.

Run "make clean" to remove the .o and .dll files in the current directory.
