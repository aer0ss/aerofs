"""
The sole purpose of this package is to adapt SP/SV's protobuf APIs into
JSON APIs. It is needed for clients who don't have protobuf support
(e.g static Web pages at www.aerofs.com).

All the routes in this package accept POST requests, and returns JSON replies.
The JSON format complies with the corresponding protobuf definitions.

WW: Now I realize how stupid it is to use binary formats for Web services.
The stupidity is due to my CORBA background. SP/SV should be redesigned to
expose REST APIs. I hope this happens sooner than Soon TM.
"""

def includeme(config):
  config.add_route('json.request_to_sign_up_with_business_plan',
      '/api/request_to_sign_up_with_business_plan')
