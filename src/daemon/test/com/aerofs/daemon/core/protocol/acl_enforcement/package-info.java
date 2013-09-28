/**
 * This test package verifies that all core protocols perform ACL enforcement rules as expected.
 * See acl.md for definitions of these rules.
 *
 * Negative tests:
 * ----
 * Each test case in this package is accompanied with a "negative" test. the nagative test shares
 * the same logic with its counterpart, except that it uses pass-through ACL and expect exceptions.
 * It is to make sure that if ACL enforcement is broken, the original, positive test will fail.
 *
 * Negative tests are necessary as some ACL enforcement tests rely on the lack of certain actions
 * (i.e. not writing to the db, not sending data) to determine test results. These tests are fragile
 * in nature and might produce false positives if the main code is updated in a way that those
 * actions will be never triggered regardless of ACL checking.
 *
 * Even though not all ACL enforcement tests rely on the lack of actions, requiring negative
 * counterparts for all the tests in this package gives us peace of mind.
 *
 */
package com.aerofs.daemon.core.protocol.acl_enforcement;
