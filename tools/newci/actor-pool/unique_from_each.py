"""
Reasonably efficient solution to the following:

Given a set of sets, return one unique element from each set, or None if none exists.

"""


class unique_from_each():
    def __init__(self, sets):
        self.sorted, self.order = zip(*sorted(((set(v), i) for (i, v) in enumerate(sets)),
                                              key=lambda t: len(t[0])))
        self.cur = []

    def get(self):
        # Return the results in the correct order, so that the n-th element returned
        # is the unique element from the n-th set
        result = self._impl(self.sorted)
        if result is None:
            return None
        return zip(*sorted(zip(self.order, result)))[1]

    def _impl(self, sets):
        if len(sets) == 0:
            return self.cur

        for ele in sets[0]:
            # Try ele and discard from future sets, remembering where to put it back
            self.cur.append(ele)
            was_present = []
            for s in sets[1:]:
                try:
                    s.remove(ele)
                    was_present.append(True)
                except KeyError:
                    was_present.append(False)
            was_present.reverse()

            # The magic goes here
            recurse = self._impl(sets[1:])

            # If the magic panned out, pass it upward
            if recurse is not None:
                return recurse

            # If the magic didn't pan out, put everything back and try a different ele
            self.cur.pop()
            for s in sets[1:]:
                if was_present.pop():
                    s.add(ele)

        # We've tried all elements in the set, and none of them work
        return None


if __name__ == "__main__":
    u = unique_from_each([[1, 2, 3], [1, 2], [1]]).get()
    assert u == (3, 2, 1)

    u = unique_from_each([[1, 2], [1, 2], [1]]).get()
    assert u is None