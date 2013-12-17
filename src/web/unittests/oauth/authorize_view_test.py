from mock import Mock
import unittest
import requests
from webob.multidict import MultiDict
from pyramid.httpexceptions import HTTPBadRequest, HTTPFound
from ..test_base import TestBase
from web.views.settings.access_tokens_view import show_authorization_page

CLIENT_ID = "CLIENT_ID"
CLIENT_NAME = "CLIENT_NAME"
REDIRECT_URI = "aerofs://redirect"
NONCE = "abcdefghijklmnop123456"

def mock_requests_get(url, **kwargs):
    client_data = {
        "client_name": CLIENT_NAME,
        "client_id": CLIENT_ID,
        "redirect_uri": REDIRECT_URI,
    }
    class Response(object):
        pass

    if url == "http://localhost:8700/clients/" + CLIENT_ID:
        r = Response()
        r.status_code = 200
        r.json = lambda: client_data
        r.ok = True
        return r
    elif url.startswith("http://localhost:8700/clients/"):
        r = Response()
        r.status_code = 404
        r.ok = False
        return r
    else:
        raise ValueError


def mock_sp_get_mobile_access_code(**kwargs):
    class Response(object):
        pass
    r = Response()
    r.accessCode = NONCE
    return r


class AuthorizeViewTest(TestBase):

    def setUp(self):
        self.setup_common()
        self.sp_rpc_stub.get_mobile_access_code = Mock(side_effect=mock_sp_get_mobile_access_code)
        requests.get = Mock(side_effect=mock_requests_get)

    def _make_req(self, params):
        request = self.create_dummy_request(MultiDict(params))
        return show_authorization_page(request)

    def test_should_400_if_client_id_or_redirect_uri_is_invalid(self):
        # missing client_id
        with self.assertRaises(HTTPBadRequest):
            self._make_req({
                "response_type": "code",
                "redirect_uri": REDIRECT_URI,
                "state": "1234567890",
            })
        # bad client_id
        with self.assertRaises(HTTPBadRequest):
            self._make_req({
                "response_type": "code",
                "client_id": "alksjdflksdjf",
                "redirect_uri": REDIRECT_URI,
                "state": "1234567890",
            })
        # missing redirect_uri
        with self.assertRaises(HTTPBadRequest):
            self._make_req({
                "response_type": "code",
                "client_id": CLIENT_ID,
                "state": "1234567890",
            })
        # bad redirect_uri
        with self.assertRaises(HTTPBadRequest):
            self._make_req({
                "response_type": "code",
                "client_id": CLIENT_ID,
                "redirect_uri": "http://malicious.com",
                "state": "1234567890",
            })

    def test_should_redirect_error_if_invalid_response_type(self):
        # no response_type
        try:
            self._make_req({
                "client_id": CLIENT_ID,
                "redirect_uri": REDIRECT_URI,
                "state": "1234567890",
            })
        except HTTPFound as e:
            self.assertIn(REDIRECT_URI, e.location)
            self.assertIn("error=invalid_request", e.location)
            self.assertIn("state=1234567890", e.location)

        # bad response_type
        try:
            self._make_req({
                "response_type": "alskdjlksj",
                "client_id": CLIENT_ID,
                "redirect_uri": REDIRECT_URI,
                "state": "1234567890",
            })
        except HTTPFound as e:
            self.assertIn(REDIRECT_URI, e.location)
            self.assertIn("error=unsupported_response_type", e.location)
            self.assertIn("state=1234567890", e.location)

    def test_should_return_consent_page_if_valid_request(self):
        to_render = self._make_req({
            "response_type": "code",
            "client_id": CLIENT_ID,
            "redirect_uri": REDIRECT_URI,
            "state": "1234567890",
        })

        # to_render is the dict that is passed to the consent page mako file
        self.assertEqual(to_render["client_name"], CLIENT_NAME)
        self.assertEqual(to_render["response_type"], "code")
        self.assertEqual(to_render["client_id"], CLIENT_ID)
        self.assertEqual(to_render["identity_nonce"], NONCE)
        self.assertEqual(to_render["redirect_uri"], REDIRECT_URI)
        self.assertEqual(to_render["state"], "1234567890")


def test_suite():
    loader = unittest.TestLoader()
    return loader.loadTestsFromName(__name__)

