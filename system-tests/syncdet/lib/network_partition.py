from lib import ritual
from syncdet.case import sync

_POST_PAUSE = "after locally paused syncing"
_PRE_RESUME = "before resuming syncing"

class NetworkPartition:
    """
    Provides a block within which the local peer is isolated from other
    peers, but other peers may still communicate with each other.

    Specfically, this is a With Statement Context Manager: before the suite to
    be executed, syncing is paused on the local AeroFS client. After the suite,
    syncing is resumed.
    """
    def __init__(self, r=None):
        """
        @param r is a ritual client
        """
        if not r: self._r = ritual.connect()
        else: self._r = r

    def __enter__(self):
        self._r.pause_syncing()

    def __exit__(self, exc_type, exc_value, traceback):
        """
        @returns false iff an exception led to the entry of this method
        see http://docs.python.org/reference/compound_stmts.html#with
        """

        # don't know how to handle exceptions right now
        if exc_type:
            print '{0} exception in with block: {1} {2}'.format(
                self.__class__, exc_type, exc_value)
            return False

        try:
            self._r.resume_syncing()
        except:
            # the old client might be stale if, e.g. the daemon was restarted
            # so we give it another shot with a fresh client...
            # NB: although we make an effort to avoid throwing an exception in
            # the event of a daemon restart this is strongly discouraged as the
            # daemon will not be in a paused syncing state when restarted so
            # the partition guarantees are severely weakened...
            ritual.connect().resume_syncing()
        return True


class GlobalNetworkPartition(NetworkPartition):
    """
    A NetworkPartition with two barriers:
    i) following the paused syncing, and  ii) just before resuming syncing

    Provides a block within which all peers are isolated. Since it uses
    two barriers internally to achieve this, it must be used by all actors
    in a scenario.

    N.B. UNSUPPORTED:
    o multiple partition/rejoin steps (this can be created/called only once
      per peer in a test)
    o we should rethink how to partition some but not all of the actors
    """

    def __init__(self, r = None):
        NetworkPartition.__init__(self, r)

    def __enter__(self):
        NetworkPartition.__enter__(self)

        # wait for other peers to pause syncing
        sync.sync(_POST_PAUSE)

    def __exit__(self, exc_type, exc_value, traceback):
        """
        Resume syncing after waiting for peers to finish the suite between
        __enter__ and __exit__
        @returns false iff an exception led to the entry of this method
        see http://docs.python.org/reference/compound_stmts.html#with
        """

        # wait for other peers to finish their work, then resume syncing
        sync.sync(_PRE_RESUME)

        return NetworkPartition.__exit__(self, exc_type, exc_value, traceback)
