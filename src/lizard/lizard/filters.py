import datetime

def timestamp_to_datetime(timestamp):
    return datetime.datetime.fromtimestamp(timestamp)

def format_currency(value):
    return "${:,.2f}".format(value)

def date(d):
    return d.strftime('%b %d, %Y')
