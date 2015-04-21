#!/usr/bin/env python
"""
This script is a first version/proof-of-concept build script to build MSI
installers on OS-X.

It operates in a very strange way due to historical reasons. The main reason
being it was not clear whether this is going to work so I simply took the most
obvious path at the time and we end up using this script because optimizing
this part of the pipeline was not a prioirity.

This is a Python script that consumes the output of find and outputs a
bash-script. It mimics wixl-heat behaviour in relying on find output and a
number of flags, and it output bash script so we can verify correctness of the
steps to be taken before taking them.

Most of the work can be done in bash. However, Python was chosen because
processing find outputs requires associative map and bash support for
associative maps is lacking at the time of writing.

Hence, we do all logical processing in Python to make full use of the
associative maps and let bash execute all the actual tasks.
"""
import argparse
import os
import string
import sys
import uuid

dirs = {}
files = {}

# dictionary of filename -> file ID
transforms = {
    'aerofs.exe':           'filAeroFS',
    'aerofsts.exe':         'filAeroFS',
    'aerofsd.exe':          'filAeroFSD',
    'AeroFSShellExt32.dll': 'filAeroFSShellExt32',
    'AeroFSShellExt64.dll': 'filAeroFSShellExt64',
    'logo.ico':             'filAeroFSIcon',
}


# parses the input (outputs from find) and populate the dictionaries
def parse_input(args):
    def uuid_gen():
        return uuid.uuid4().hex.upper()

    for path in [line[:-1] for line in sys.stdin if line.startswith(args.directory)]:
        if path == args.directory:
            dirs[path] = {
                'source':   path,
                'id':       'dir{}'.format(uuid_gen()),
                'parent':   args.directory_ref,
                'name':     '.',
            }
        elif os.path.isdir(path):
            dirs[path] = {
                'source':   path,
                'id':       'dir{}'.format(uuid_gen()),
                'parent':   dirs[os.path.dirname(path)]['id'],
                'name':     os.path.basename(path),
            }
        elif os.path.isfile(path):
            files[path] = {
                'source':   path,
                'id':       'fil{}'.format(uuid_gen()),
                'parent':   dirs[os.path.dirname(path)]['id'],
                'name':     os.path.basename(path),
                'size':     os.path.getsize(path),
            }


def apply_transforms():
    for file in [file for file in files.values() if file['name'] in transforms]:
        file['id'] = transforms[file['name']]


# The generated script is vulnerable to SQL injection attacks.
# Please think of the cats and don't launch SQL injection attacks against the
# poor build script.
def generate_script(args):
    def print_query(query):
        print 'msibuild {} -q "{}"'.format(wip_msi, query)

    def guid_gen():
        return '{{{}}}'.format(str(uuid.uuid4()).upper())

    wip_msi = '{}/{}'.format(args.workspace, 'wip.msi')

    print '#!/bin/bash'
    print 'set -e'
    print 'rm -rf {}'.format(args.workspace)
    print 'mkdir -p {}'.format(args.workspace)
    print 'cp {} {}'.format(args.base_msi, wip_msi)

    # update the database tables related to files
    for seq, file, comp in [(seq + 1, files[file], 'cmp{}'.format(files[file]['id'][3:])) for (seq, file) in enumerate(files)]:
        if file['id'] in transforms.values():
            print_query("update Component set Directory_='{}' where Component='{}';".format(
                        file['parent'], comp))
            print_query("update File set FileSize={}, Sequence={}, FileName='{}' where File='{}';".format(
                        file['size'], seq, file['name'], file['id']))
        else:
            print_query("insert into FeatureComponents(Feature_, Component_) values('AeroFSFeatures', '{}');".format(
                        comp))
            print_query("insert into Component(Component, ComponentId, Directory_, Attributes, KeyPath) "
                        "values('{}', '{}', '{}', 0, '{}');".format(
                        comp, guid_gen(), file['parent'], file['id']))
            print_query("insert into File(File, Component_, FileName, FileSize, Attributes, Sequence) "
                        "values('{}', '{}', '{}', {}, 512, {});".format(
                        file['id'], comp, file['name'], file['size'], seq))

    # update the database tables related to directories
    for dir in [dirs[dir] for dir in dirs]:
        print_query("insert into Directory(Directory, Directory_Parent, DefaultDir) "
                    "values('{}', '{}', '{}');".format(
                    dir['id'], dir['parent'], dir['name']))

    # the shim contains file hashes of the stub files. Since the file hashes may
    # interfere with the installation, let's delete the file hashes.
    #
    # note that it's necessary to run the query 3 times because not all rows are
    # deleted each time (why? I have no clue but I suspect foul key sorcery)
    #
    # note that simply dropping the table does not work because the table schema
    # is protected by validations.
    for x in range(0, 3):
        print_query('delete from MsiFileHash;')

    # building cab archive
    for file in files.values():
        print 'cp {} {}/{}'.format(file['source'], args.workspace, file['id'])

    print 'pushd {} > /dev/null'.format(args.workspace)
    print 'gcab -c cab1.cab {}'.format(string.join([file['id'] for file in files.values()], ' \\\n'))
    print 'popd > /dev/null'
    print 'msibuild {} -a cab1.cab {}/cab1.cab'.format(wip_msi, args.workspace)
    print_query('update Media set LastSequence={} where DiskId=1;'.format(len(files)))

    # this is to replace the app icon used in shortcuts, Add Remove Programs, etc.
    #
    # note that Windows shell do cache those icons so changes will probably not
    # be observed until the user exit and log in to the shell.
    print 'msibuild {} -a Icon.logo.ico {}/filAeroFSIcon'.format(wip_msi, args.workspace)

    # update product version
    print_query("update Property set Value='{}' where Property='ProductVersion';".format(args.set_version))

    # update product code
    print_query("update Property set Value='{}' where Property='ProductCode';".format(guid_gen()))

    # update upgrade versions
    print "msiinfo export {} Upgrade | sed 's/1\.0\.0/{}/g' > {}/Upgrade.idt".format(wip_msi, args.set_version, args.workspace)
    print 'msibuild {} -i {}/Upgrade.idt'.format(wip_msi, args.workspace)

    # update package code
    print 'msiinfo export {} _SummaryInformation | grep -v "^9\t" > {}/_SummaryInfo.idt'.format(wip_msi, args.workspace)
    print 'echo -e "9\t{}" >> {}/_SummaryInfo.idt'.format(guid_gen(), args.workspace)
    print 'msibuild {} -i {}/_SummaryInfo.idt'.format(wip_msi, args.workspace)

    # finalize
    print 'cp {} {}'.format(wip_msi, args.output_msi)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--directory-ref', default='INSTALLLOCATION')
    parser.add_argument('--base-msi', required=True)
    parser.add_argument('--output-msi', required=True)
    parser.add_argument('--directory', required=True)
    parser.add_argument('--workspace', required=True)
    parser.add_argument('--set-version', default='100.0.0')
    args = parser.parse_args(sys.argv[1:])

    parse_input(args)
    apply_transforms()
    generate_script(args)


if __name__ == '__main__':
    main()
