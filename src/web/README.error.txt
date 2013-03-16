
TODO (WW) finish this doc in a separate commit.

To return success without data, use "return {}":

@view_config(
    route_name='foo',
    renderer='json',
    request_method='POST',
)
def foo():
    # ...
    return {}