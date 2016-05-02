# - doctor version file
# - doctor manifest to prevent detection of doctored version
# - restart client
# - wait (10s) for update to kick in
# - detect new version file
# - make sure daemon is ritual-reachable

import os
import time
import json
import hashlib
from lib.app import cfg, aerofs_proc


def down_and_up():
    c = cfg.get_cfg()

    approot = c.get_approot()

    vf = os.path.join(approot, "current", "version")

    mo = hashlib.sha256()
    mn = hashlib.sha256()

    # doctor version file to force upgrade
    with open(vf, "r+") as f:
        ov = f.read(3)
        mo.update(ov)
        print "found version", ov
        f.seek(0)
        f.write("0.0")
        f.seek(0)
        nv = f.read(3)
        mn.update(nv)
        print "downgraded to", nv
        d = f.read()
        mo.update(d)
        mn.update(d)

    ho = mo.hexdigest()
    hn = mn.hexdigest()
    print ho, hn

    # fix manifest to account for doctored version
    mf = os.path.join(approot, "manifest.json")
    with open(mf, "r") as f:
        m = json.load(f)

    print m["files"]["version"]
    m["files"]["version"][1] = hn
    print m["files"]["version"]

    with open(mf, "w") as f:
        json.dump(m, f)

    # restart app with doctored version
    aerofs_proc.stop_all()
    aerofs_proc.run_ui()

    # NB: wait for updater to do its job
    # The updater waits 10s before the first version check after launch
    # and it takes a few more for the update to be downloaded and applied
    print "wait for update check"
    time.sleep(12)

    # wait for version file to reflect successful upgrade
    n = 15
    while True:
        try:
            with open(vf, "r") as f:
                v = f.readline().strip()
                #print "v: ", v
                if v[0] != '0':
                    print "back to ", v
                    break
                if n == 0:
                    raise AssertionError
        except IOError:
            if n == 0:
                raise
        time.sleep(1)
        n -= 1

    # make sure daemon comes back up after upgrade
    aerofs_proc.wait_for_daemon()

spec = {"default": down_and_up}
