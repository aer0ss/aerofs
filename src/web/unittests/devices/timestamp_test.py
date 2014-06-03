import unittest
import time, datetime

class RenderTimestampTest(unittest.TestCase):

    def _render_timestamp(self, timestamp, interval=0):
        from web.views.devices.devices_view import _pretty_timestamp

        return _pretty_timestamp(interval, timestamp)

    def _to_posix(self, d):
        # converts datetime object to posix timestamp, returns it
        return time.mktime(d.timetuple())

    def test_just_now(self):
        self.assertEquals(self._render_timestamp(self._to_posix(datetime.datetime.now()), interval=1), "just now")

    def test_one_second(self):
        d = datetime.datetime.now() - datetime.timedelta(seconds=1)
        self.assertEquals(self._render_timestamp(self._to_posix(d)), "a second ago")

    def test_two_seconds(self):
        d = datetime.datetime.now() - datetime.timedelta(seconds=2)
        self.assertEquals(self._render_timestamp(self._to_posix(d)), "2 seconds ago")

    def test_one_minute(self):
        d = datetime.datetime.now() - datetime.timedelta(seconds=60)
        self.assertEquals(self._render_timestamp(self._to_posix(d)), "a minute ago")

    def test_one_and_a_half_minutes(self):
        d = datetime.datetime.now() - datetime.timedelta(seconds=90)
        self.assertEquals(self._render_timestamp(self._to_posix(d)), "a minute ago")

    def test_three_minutes(self):
        d = datetime.datetime.now() - datetime.timedelta(seconds=180)
        self.assertEquals(self._render_timestamp(self._to_posix(d)), "3 minutes ago")

    def test_an_hour(self):
        d = datetime.datetime.now() - datetime.timedelta(hours=1)
        self.assertEquals(self._render_timestamp(self._to_posix(d)), "an hour ago")

    def test_two_hours(self):
        d = datetime.datetime.now() - datetime.timedelta(hours=2)
        self.assertEquals(self._render_timestamp(self._to_posix(d)), "2 hours ago")

    def test_yesterday(self):
        d = datetime.datetime.now() - datetime.timedelta(hours=24)
        self.assertEquals(self._render_timestamp(self._to_posix(d)), "yesterday")

    def test_two_days(self):
        d = datetime.datetime.now() - datetime.timedelta(hours=48)
        self.assertEquals(self._render_timestamp(self._to_posix(d)), "2 days ago")

    def test_one_week(self):
        d = datetime.datetime.now() - datetime.timedelta(days=10)
        self.assertEquals(self._render_timestamp(self._to_posix(d)), "a week ago")

    def test_two_weeks(self):
        d = datetime.datetime.now() - datetime.timedelta(days=14)
        self.assertEquals(self._render_timestamp(self._to_posix(d)), "2 weeks ago")

    def test_a_month(self):
        d = datetime.datetime.now() - datetime.timedelta(days=31)
        self.assertEquals(self._render_timestamp(self._to_posix(d)), "a month ago")

    def test_two_months(self):
        d = datetime.datetime.now() - datetime.timedelta(weeks=9)
        self.assertEquals(self._render_timestamp(self._to_posix(d)), "2 months ago")

    def test_a_year(self):
        d = datetime.datetime.now() - datetime.timedelta(days=365)
        self.assertEquals(self._render_timestamp(self._to_posix(d)), "a year ago")

    def test_a_century(self):
        d = datetime.datetime.now() - datetime.timedelta(days=36500)
        self.assertEquals(self._render_timestamp(self._to_posix(d)), "100 years ago")


def test_suite():
    loader = unittest.TestLoader()
    return loader.loadTestsFromName(__name__)
