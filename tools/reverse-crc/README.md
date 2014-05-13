reverse-crc
===========

reverse-crc.py is a tool for reversing CRC32-obfuscation, used in the daemon to hide paths.
It won't guess all words, but often it'll give you enough context to figure out what should be going on.

Usage:
------

### Reverse map a single string:

    $ python reverse-crc.py /71d60cd0/d3d8f7b6/f2e88c90/146f3ea3/20b8ff21
    /home/d3d8f7b6/AeroFS/ref/20b8ff21

### Reverse map a log file, given a defect map file

    $ cat defect-818584/daemon.log | python reverse-crc.py -f defect-818584/name3758872731124740232map > defect-818584/daemon.log.re
