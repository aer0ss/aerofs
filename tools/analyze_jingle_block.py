#!/usr/bin/env python
"""
This script reads parses a daemon log for lines which indicate that the jingle transport is
blocking. For each of these lines, it calculates the time that elapses until the transport
is unblocked. The script then produces a histogram of these times.

Note:
- elapsed times of 0 milliseconds are IGNORED. Only 1+ ms blocks are plotted.
- the script can take multiple logs as args, and will generate the histograms in one figure
- this script runs much, much faster if you grep for jingle-related lines and pass that in.
    a typical use-case is as follows:

    grep "jds:send" daemon.log > send.log
    grep "jds:recv" daemon.log > recv.log
    ./analyze_jingle_block.py send.log recv.log
"""

import sys
import re
import matplotlib.pyplot as plt

REGEX = "([0-9]{6})\.([0-9]{3})[^\n]*?SR_BLOCK.*?([0-9]{6})\.([0-9]{3})[^\n]*?SR_SUCCESS"


def to_ms(hms, ms):
    return int(hms[:2])*60*60*1000 + int(hms[2:4])*60*1000 + int(hms[4:])*1000 + int(ms)


def main():
    nfiles = len(sys.argv) - 1
    for idx, filename in enumerate(sys.argv[1:]):
        print 'reading file...'
        with open(filename, 'r') as f:
            contents = f.read()

        print 'doing regex magic...'
        matches = re.findall(REGEX, contents, re.DOTALL)

        print 'analyzing times...'
        deltas = [to_ms(m[2], m[3]) - to_ms(m[0], m[1]) for m in matches]

        print 'generating plot...'
        ax = plt.subplot(nfiles, 1, idx+1)
        n, bins, patches = ax.hist(deltas, bins=50, range=(1, 200))
        ax.set_title(filename)

    plt.show()


if __name__ == "__main__":
    main()