import os
from lib.app import aerofs_proc
from lib.files import instance_path, wait_dir
from syncdet.case.sync import sync
from syncdet.case.assertion import assertTrue, assertFalse
from lib import ritual

# NB: this test has been superseded by core.misc.should_move_read_only_file


def nro():
    print 'nro', instance_path("foo")

    os.mkdir(instance_path())
    os.chmod(instance_path(), 0555)

    sync(1)

    ritual.connect().wait_path(instance_path("foo", "bar"))

    # should be NRO
    assertFalse(os.path.exists(instance_path("foo", "bar")))

    aerofs_proc.stop_all()

    os.chmod(instance_path(), 0755)
    os.makedirs(instance_path("foo", "baz"))
    os.chmod(instance_path(), 0555)

    aerofs_proc.run_ui()

    r = ritual.connect()
    r.wait_path(instance_path("foo (2)", "bar"))
    r.wait_path(instance_path("foo", "baz"))

    assertTrue(os.path.exists(instance_path("foo", "baz")))
    assertFalse(os.path.exists(instance_path("foo (2)", "bar")))

    # do not leave unwritable folders around
    # as it would cause future clean_install to fail
    os.chmod(instance_path(), 0755)


def put():
    print 'put', instance_path("foo")

    wait_dir(instance_path())
    sync(1)

    os.makedirs(instance_path("foo", "bar"))

    _wait_final_state()


def get():
    print 'get', instance_path("foo")

    wait_dir(instance_path())
    sync(1)

    _wait_final_state()


def _wait_final_state():
    r = ritual.connect()
    r.wait_path(instance_path("foo (2)", "bar"))
    r.wait_path(instance_path("foo", "baz"))

    assertTrue(os.path.exists(instance_path("foo", "baz")))
    assertTrue(os.path.exists(instance_path("foo (2)", "bar")))


spec = {'entries': [nro, put], 'default': get}
