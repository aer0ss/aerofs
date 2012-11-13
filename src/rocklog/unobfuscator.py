import itertools
import collections
import logging
from subprocess import Popen, PIPE, STDOUT

ObfName = collections.namedtuple('ObfName', ['classname', 'method', 'line'])

class Unobfuscator:
    """
    This class manages the unobfuscation of class and method names

    Because the unobfuscation is done by an external Java tool (retrace) and that tool is pretty slow to start, we
    try to batch unobfuscation queries and to cache the results.

    This is why this class has two important public methods:

    unobfuscate_and_cache() unobfuscates a list of ObfName and save them in the cache
    get_unobfuscated()      get an un-obfuscated name from the cache. If the name is not in the cache, this method
                            will return the original (obfuscated) name
    """

    # LRU cache for the unobfuscated names
    # Keys are tuple (ObfName, version)
    # Values are a the unobfuscated string representation of the ObfName
    # Order: least recent to most recent.
    unobfcache = collections.OrderedDict()

    def __init__(self, cache_size):
        self.cache_size = cache_size
        self.logger = logging

    def retrace_map_filename(self, version):
        return '/maps/aerofs-%s-prod.map' % version

    def stackframe_to_string(self, classname, method = None, line = None):
        result = classname
        if method: result += " in " + method
        if line:   result += " at " + str(line)
        return result

    def unobfuscate_and_cache(self, obfnames, version):
        """
        Calls Proguard's retrace to unobfuscate the names for a given AeroFS version
        The results will be saved into a LRU cache and accessible with 'get_unobfuscated()'
        This method doesn't return anything

        names: a list of ObfName
        version: the AeroFS version
        """

        retrace_input = retrace_output = retrace_stderr = None

        try:
            # Keep only names from the com.aerofs package not already in the cache
            needs_unobf = set([n for n in obfnames if
                               n.classname.startswith("com.aerofs")
                               and not self.unobfcache.has_key((n, version))])

            retrace = Popen(['java', '-jar', 'retrace.jar', '-regex', '"%c|%l|%m"', self.retrace_map_filename(version)],
                stdout=PIPE, stdin=PIPE, stderr=PIPE)
            retrace_input = ''.join(["%s|%s|%s\n" % (n.classname, n.line or '', n.method or '') for n in needs_unobf])
            retrace_output, retrace_stderr = retrace.communicate(retrace_input)

            unobf = []

            # In some cases retrace can't map a method to a single non-obfuscated name. In those cases, several possible
            # method names will each be printed on a new line with spaces in the beginning
            for line in retrace_output.splitlines():
                if line.startswith(' '):
                    unobf[-1]['method'] += ", " + line.strip()
                else:
                    n = line.split('|')
                    unobf.append({'class':n[0], 'method':n[2], 'line':n[1]})

            # Now hopefully we should end up with a list of unobfuscated names that match the list of obfuscated names
            assert len(needs_unobf) == len(unobf)

            to_cache = [self.stackframe_to_string(n['class'], n['method'], n['line']) for n in unobf]

            # Remove the n oldest items from the cache.
            n = max(0, min(len(self.unobfcache) + len(to_cache) - self.cache_size, len(self.unobfcache)))
            for _ in itertools.repeat(None, n):
                self.unobfcache.popitem(0)

            # And finally, add the items to the cache
            self.unobfcache.update(zip(zip(needs_unobf, itertools.repeat(version)), to_cache))

        except:
            self.logger.exception("UNOBFUSCATION ERROR\n"
                                  "input:\n%s\n"
                                  "output:\n%s\n"
                                  "stderr:\n%s" % (retrace_input, retrace_output, retrace_stderr))


    def get_unobfuscated(self, obfname, version):
        """
        Reads a single ObfName from the LRU cache and return its string representation
        If the item is not found in the cache, it will return the obfuscated name.
        Note: this method doesn't call Proguard's retrace at all. Use 'unobfuscate_and_cache'
        to do this and populate the cache.
        """
        assert type(obfname) is ObfName
        key = (obfname, version)
        try:
            result = self.unobfcache.pop(key)
            self.unobfcache[key] = result     # put the result back at the top of the cache
        except KeyError:
            result = self.stackframe_to_string(obfname.classname, obfname.method, obfname.line)

        return result
