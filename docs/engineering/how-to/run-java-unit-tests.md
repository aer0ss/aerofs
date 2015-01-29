How To Run Java Unit Tests
---

All tests:

    gradle test

A single suite:

    gradle :src/spsv:test

A single test case within a suite:

    gradle -Dtest.single=TestSP_Unlink :src/spsv:test
