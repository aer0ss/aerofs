#!/usr/bin/env python
import sys
import re


def get_retrace_map(mapfile):
    """
    mapfile: iterator through a proguard mapfile
    returns:
      - a dict from com.aerofs.proto.lM -> com.aerofs.proto.Sp$GetOrganizationIDReply$Builder
      - a dict from lM -> com.aerofs.proto.Sp$GetOrganizationIDReply$Builder

    """
    fullmap = {}
    tailmap = {}
    for line in mapfile:
        if not line.startswith('com.aerofs'):
            continue
        unobf, obf = line.strip().rstrip(':').split(' -> ')
        fullmap[obf] = unobf  # com.aerofs.proto.lM -> com.aerofs.proto.Sp$GetOrganizationIDReply$Builder
        tail = obf[obf.rfind('.')+1:]  # lM
        if tail in tailmap:
            # if there is a com.aerofs.lM and a com.aerofs.proto.lM, then @lM
            # could mean either one. Do the safe thing and do not de-obfuscate
            # in this situation
            tailmap[tail] = None
        else:
            tailmap[tail] = unobf  # lM -> com.aerofs.proto.Sp$GetOrganizationIDReply$Builder
    return fullmap, tailmap


def retrace(mapf, mapt, lines):
    """
    mapf: full name map dict returned by get_retrace_map
    mapt: tail name map dict returned by get_retrace_map
    lines: iterator through obfuscated lines
    yields: unobfuscated lines
    """
    for line in lines:
        yield retrace_line(mapf, mapt, line)


def retrace_line(mapf, mapt, line):
    unobf = line

    # match full class name
    matches = re.findall('com\.aerofs[.a-zA-Z]+', line)
    for match in matches:
        while match != 'com.aerofs':
            if match in mapf:
                unobf = unobf.replace(match, mapf[match])
            match = match[:match.rfind('.')]  # com.aerofs.ab.cd -> com.aerofs.ab

    # match @obj
    matches = re.findall('~([a-zA-Z]+)~', line)
    for match in matches:
        u = mapt.get(match)
        if u is not None:
            unobf = unobf.replace('~{}~'.format(match), '~{}~'.format(u[u.rfind('.')+1:]))

    return unobf


def main(mapfile, obfuscated):
    with open(mapfile, 'r') as f:
        mapf, mapt = get_retrace_map(f)

    with open(obfuscated, 'r') as f:
        for unobf in retrace(mapf, mapt, f):
            yield unobf.rstrip()


if __name__ == '__main__':
    if len(sys.argv) != 3:
        sys.stderr.write('Usage: {} [mapfile] [obfuscated]\n'.format(sys.argv[0]))
        sys.exit(1)

    mapfile = sys.argv[1]
    obffile = sys.argv[2]

    for line in main(mapfile, obffile):
        print line

