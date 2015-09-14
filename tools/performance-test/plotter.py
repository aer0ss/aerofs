#!/usr/bin/env python
"""Plotter

Usage:
  plotter.py [--metrics=<dir>] [--output=<name>] [-s | --slope] <attributes>...
  plotter.py (-h | --help)

Options:
  -m --metrics=<dir>   Metrics directory [default: ~/metrics].
  -o --output=<name>   Output filename [default: ./plotted_metrics.png].
  -s --slope           Draw slopes for each attribute instead.
  -h --help            Show this screen.
"""
import datetime
import glob
import json
import os

import docopt
import matplotlib.pyplot as plot
import numpy


def get_dates_from_filenames(folder):
    filenames = [os.path.splitext(filename)[0]
                 for filename in glob.glob1(folder, '*.json')]

    dates = []
    names = []
    for name in filenames:
        try:
            date = datetime.datetime.strptime(name, '%H-%M-%Sd%m-%d')
            dates.append(date)
            names.append(os.path.join(folder, name + '.json'))
        except ValueError:
            print 'WARN: ignoring unrecognized file format of {}'.format(name)

    return names, dates


def get_value(filename, attr):
    with open(filename) as f:
        try:
            obj = json.load(f)
        except Exception:
            print 'WARN: ignoring broken file {}'.format(filename)

    for field in [piece for piece in attr.split('.') if piece]:
        obj = obj.get(field, dict())

    try:
        return float(obj)
    except TypeError:
        return 0.


def plot_attributes(attributes, dates, files, slope=False):
    cmap = 'bgrcmykw'

    ax = plot.subplot(111)
    for i, attr in enumerate(attributes):
        if slope:
            derivative = numpy.array([get_value(f, attr) for f in files],
                                     dtype=numpy.float)
            gradient = numpy.gradient(derivative).clip(-100, 10000)
            ax.plot(dates, gradient, color=cmap[i],
                    label='Slope of {}'.format(attr))
            continue

        ax.plot(dates, [get_value(f, attr) for f in files], color=cmap[i],
                label=attr)

    plot.gcf().autofmt_xdate()

    box = ax.get_position()
    ax.set_position([box.x0, box.y0 + box.height * 0.25,
                     box.width, box.height * 0.75])
    ax.legend(loc='upper center', bbox_to_anchor=(0.5, -0.26),
              fancybox=True, shadow=True, ncol=1)


def main():
    arguments = docopt.docopt(__doc__)
    metric_dir = os.path.expanduser(arguments['--metrics'])
    output_name = os.path.expanduser(arguments['--output'])

    files, dates = get_dates_from_filenames(metric_dir)

    plot.title('Plotted Metrics')
    plot_attributes(arguments['<attributes>'], dates, files,
                    slope=arguments['--slope'])

    plot.savefig(output_name, format='png')


if __name__ == '__main__':
    main()
