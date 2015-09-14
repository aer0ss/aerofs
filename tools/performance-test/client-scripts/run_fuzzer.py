#!/usr/bin/env python
"""Fuzzer

Create filesystem operations for AeroFS client to process.

Usage:
  fuzzer.py [--delay=<seconds>] [--objects=<num>] [--fuzz=<amount>]
  fuzzer.py (-h | --help)

Options:
  -d --delay=<seconds>  Delay between fs operations [default: 0.02].
  -o --objects=<num>    Number of object per client [default: 100].
  -f --fuzz=<amount>    Number of fuzz operations per client [default: 1000].
  -h --help         Show this screen.
"""
import os

import docopt

import fuzzer


def main():
    arguments = docopt.docopt(__doc__)
    delay = float(os.path.expanduser(arguments['--delay']))
    objects = int(os.path.expanduser(arguments['--objects']))
    fuzz = int(os.path.expanduser(arguments['--fuzz']))

    fuzzer.fuzzer(objects, fuzz, delay)


if __name__ == '__main__':
    main()
