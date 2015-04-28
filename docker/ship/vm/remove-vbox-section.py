#!/usr/bin/env python
import argparse
from hashlib import sha1
import os.path
import re
from shutil import rmtree
import tarfile
from tempfile import mkdtemp
import xml.etree.ElementTree as XMLTree


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument('ova_file', help="an tarfile containing a .ovf file")
    args = parser.parse_args()
    return args


def extract_tarfile_to_dir(ova_file, extract_dir):
    with tarfile.open(ova_file, 'r') as tf:
        members = tf.getnames()
        tf.extractall(extract_dir)

    # return the absolute path of the three files in the tar directory. The OVA
    # format specifies that the files must be in this order, and Tarfile.getnames()
    # will return the names in the proper order. The assertions are there for sanity.
    ovffile, vmdkfile, mffile = [os.path.join(extract_dir, name) for name in members]
    assert ovffile[-3:] == 'ovf'
    assert vmdkfile[-4:] == 'vmdk'
    assert mffile[-2:] == 'mf'
    return ovffile, vmdkfile, mffile


def get_namespace(xmlfile, nsparam):
    """
    If nsparam were 'vbox' and xmlfile contained the line:

        xmlns:vbox="http://www.virtualbox.org/ovf/machine"

    then return "http://www.virtualbox.org/ovf/machine"
    """
    pattern = 'xmlns:{}="([^"]*)"'.format(nsparam)
    with open(xmlfile, 'r') as f:
        for line in f:
            r = re.search(pattern, line)
            if r is None:
                continue
            return r.group(1)
    # if the for loop didn't return anything, no such namespace exists
    return None


def remove_xml_block(xmlfile, tagstring):
    """ remove all tags from an xml-formatted file containing "tagstring" in the tag. """

    def remove_matching_children(root):
        matching_children = [c for c in list(root) if tagstring in c.tag]
        for c in matching_children:
            print 'removing', c
            root.remove(c)
        for c in list(root):
            remove_matching_children(c)

    tree = XMLTree.parse(xmlfile)
    remove_matching_children(tree.getroot())
    tree.write(xmlfile)


def change_system_type(filename):
    with open(filename, 'r') as f:
        contents = f.read()
    new_contents = re.sub('virtualbox-[0-9.]*', 'vmx-7', contents)
    with open(filename, 'w') as f:
        f.write(new_contents)


def filesha1sum(filename, READ_CHUNK_SIZE=32768):
    sha = sha1()
    with open(filename, 'rb') as f:
        while True:
            readbytes = f.read(READ_CHUNK_SIZE)
            sha.update(readbytes)
            if len(readbytes) < READ_CHUNK_SIZE:
                return sha.hexdigest()


def rewrite_manifest_shasum(manifest, changed):
    """
    rewrite_manifest_shasum(manifest, 'file2') changes

        SHA1 (file1)= abc123...
        SHA1 (file2)= <old hash>

    to:

        SHA1 (file1)= abc123...
        SHA1 (file2)= <new hash>
    """
    with open(manifest, 'r') as f:
        mf_lines = f.readlines()
    new_shasum = filesha1sum(changed)
    pattern = 'SHA1 ({})= '.format(os.path.basename(changed))
    new_mf_lines = []
    for line in mf_lines:
        if line.startswith(pattern):
            new_mf_lines.append(pattern + new_shasum)
        else:
            new_mf_lines.append(line)
    with open(manifest, 'w') as f:
        f.write('\n'.join(new_mf_lines))


def tar_and_save(outfile, *args):
    """ Tar all files in *args to outfile. """
    with tarfile.open(outfile, 'w') as tf:
        for filename in args:
            tf.add(filename, arcname=os.path.basename(filename))


def main():
    args = parse_args()
    tempdir = mkdtemp()
    try:
        print 'extracting ova...'
        ovffile, vmdkfile, mffile = extract_tarfile_to_dir(args.ova_file, tempdir)
        vbox_xmlns = get_namespace(ovffile, 'vbox')
        if vbox_xmlns is None:
            print "no vbox namespace exists in this XML file. We're done here."
            exit(0)
        remove_xml_block(ovffile, vbox_xmlns)
        change_system_type(ovffile)
        print 'rewriting manifest file...'
        rewrite_manifest_shasum(mffile, ovffile)
        print 'repackaging ova...'
        tar_and_save(args.ova_file, ovffile, vmdkfile, mffile)
    finally:
        print 'cleaning up...'
        rmtree(tempdir)


if __name__ == '__main__':
    main()
