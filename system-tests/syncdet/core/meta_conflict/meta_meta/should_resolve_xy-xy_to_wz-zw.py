"""
initial state
-------------
A,B: (foo,o1), (bar,o2)

in partition
------------
A: (qux,o1), (baz,o2)
B: (baz,o1), (qux,o2)

final state in
---------------
A,B: (qux,o1), (baz,o2)
A,B: (baz,o1), (qux,o2)
A,B: (qux (2),o1), (qux,o2)
A,B: (qux,o1), (qux (2),o2)
A,B: (baz (2),o1), (baz,o2)
A,B: (baz,o1), (baz (2),o2)

Depending on DID and message ordering
"""

from . import meta_meta_spec, move

def op1(r):
    move(r, "foo", "qux")
    move(r, "bar", "baz")


def op2(r):
    move(r, "foo", "baz")
    move(r, "bar", "qux")


spec = meta_meta_spec(
    # initial state
    initial={
        "foo": "fool",
        "bar": "bart"
    },
    # acceptable final states
    final=[{
        "qux": "fool",
        "baz": "bart"
    }, {
        "baz": "fool",
        "qux": "bart"
    }, {
        "qux (2)": "fool",
        "qux": "bart"
    }, {
        "qux": "fool",
        "qux (2)": "bart"
    }, {
        "baz (2)": "fool",
        "baz": "bart"
    }, {
        "baz": "fool",
        "baz (2)": "bart"
    }, {
        "qux (2)": "fool",
        "baz": "bart"
    }, {
        "baz": "fool",
        "qux (2)": "bart"
    }, {
        "baz (2)": "fool",
        "qux": "bart"
    }, {
        "qux": "fool",
        "baz (2)": "bart"
    }],
    # operations done in a network partition on different actors
    ops=[op1, op2])