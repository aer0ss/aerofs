"""
A spot to store common methods for command line parsing, etc.
"""

import sys
import logging
import getopt

def server_usage():
    """
    Parse command line options for the vmhost/kvm/admin server applications.
    """

    print 'Usage:', sys.argv[0], '[options]'
    print 'Available options:'
    print '  -v  verbose.'
    print '  -f  print log messages to console (foreground).'

def server_parseopts(name, log_file):
    """
    Parse standard server command line options and return a logger.
    """

    # Logging facility.
    fmt = '%(asctime)-6s %(name)s:%(levelname)s %(message)s'
    logger = logging.getLogger(name)
    hdlr = logging.FileHandler(log_file)
    formatter = logging.Formatter(fmt)
    hdlr.setFormatter(formatter)
    logger.addHandler(hdlr)
    logger.setLevel(logging.ERROR)

    # Parse options.
    try:
        opts, args = getopt.getopt(sys.argv[1:], "vf")
    except getopt.GetoptError, err:
        print str(err)
        server_usage()
        sys.exit(1)
    verbose = False
    foreground = False
    for o, a in opts:
        if o == "-v":
            logger.setLevel(logging.DEBUG)
        elif o == "-f":
            logging.basicConfig(format=fmt)
        else:
            assert False, "unhandled option"

    return logger
