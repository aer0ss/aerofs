"""
initial state
-------------
A,B: (foo,o1), (bar,o2)

in partition
------------
A: (bar,o1), (trash/bar,o2)
B: (trash/foo,o1), (foo,o2)

final state in
--------------
A,B: (bar,o1), (trash/bar,o2)
A,B: (trash/foo,o1), (foo,o2)

Depending on DID ordering

NB: this might change in the future if we resolve delete/rename conflict
differently (favoring either delete or rename consistently)
"""

from . import meta_meta_spec, move, delete

def op1(r):
    delete(r, "bar")
    move(r, "foo", "bar")


def op2(r):
    delete(r, "foo")
    move(r, "bar", "foo")


spec = meta_meta_spec(
    # initial state
    initial={
        "foo": "fool",
        "bar": "bart"
    },
    # acceptable final states
    final=[{
        "bar": "fool"
    }, {
        "foo": "bart"
    }, {
        # polaris: delete always wins
    }],
    # operations done in a network partition on different actors
    ops=[op1, op2])