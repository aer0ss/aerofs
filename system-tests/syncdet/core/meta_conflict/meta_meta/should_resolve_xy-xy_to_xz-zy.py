"""
initial state
-------------
A,B: (foo,o1), (bar,o2)

in partition
------------
A: (foo,o1), (baz,o2)
B: (baz,o1), (bar,o2)

final state in
--------------
A,B: (baz (2),o1), (baz,o2)
A,B: (baz,o1), (baz (2),o2)

Depending on message ordering
"""

from . import meta_meta_spec, move

def op1(r):
    move(r, "bar", "baz")


def op2(r):
    move(r, "foo", "baz")


spec = meta_meta_spec(
    # initial state
    initial={
        "foo": "fool",
        "bar": "bart"
    },
    # acceptable final states
    final=[{
        "foo": "fool",
        "baz": "bart"
    }, {
        "baz": "fool",
        "bar": "bart"
    }, {
        "baz": "fool",
        "baz (2)": "bart"
    }, {
        "baz (2)": "fool",
        "baz": "bart"
    }],
    # operations done in a network partition on different actors
    ops=[op1, op2])