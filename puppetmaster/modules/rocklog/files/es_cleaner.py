#!/usr/bin/env python

"""
Deletes all indices older than the indicated duration in days.

User can provide a prefix and a date format, from which we build a
simple index-name parser.

Requires pyelasticsearch
"""

import argparse
from datetime import datetime, timedelta
import pyelasticsearch


def make_parser():
    """ Get ready to parse the command line options. """
    parser = argparse.ArgumentParser(description='Scan and delete old daily indices from ElasticSearch.')

    parser.add_argument('-u', '--url', default='http://localhost:9200/',
                        help='elastic search URL (including port)')
    parser.add_argument('-t', '--timeout', default=30, type=int,
                        help='server timeout in seconds')
    parser.add_argument('-p', '--prefix', default='defects-',
                        help='index name prefix string')
    parser.add_argument('-f', '--date-format', default='%Y-%m-%d',
                        help='index name date format string, as used by strptime()')
    parser.add_argument('-d', '--days-to-keep', action='store', required=True,
                        help='indices older than this value will be deleted', type=int)
    parser.add_argument('-n', '--dry-run', action='store_true', default=False,
                        help='If true, does not perform any changes to the indices.')

    return parser


def get_expired_indices(es, args):
    """
    Return the set of index names that are older than args.days
    :param es: a valid ES connection
    :param args: the arguments object containing date_format, prefix, and days_to_keep
    :return: a list of filtered index names
    """
    cutoff_date = datetime.today() - timedelta(days=args.days_to_keep)
    date_format = args.prefix + args.date_format

    return [idx for idx in es.status('_all')['indices']
            if idx.startswith(args.prefix) and (datetime.strptime(idx, date_format) < cutoff_date)
            ]


def print_index(es, idx):
    print '[dry-run] Would delete ' + idx


def delete_index(es, idx):
    es.delete_index(idx)


def main():
    arguments = make_parser().parse_args()

    es = pyelasticsearch.ElasticSearch(arguments.url, arguments.timeout)

    print ''
    print 'Deleting daily indices older than {0} days.'.format(arguments.days_to_keep)
    print ''

    deleter = print_index if arguments.dry_run else delete_index
    for index in get_expired_indices(es, arguments):
        deleter(es, index)


if __name__ == '__main__':
    main()
