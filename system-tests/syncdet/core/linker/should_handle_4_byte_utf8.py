"""
JNotify used to choke on 4-bytes UTF-8 characters because it used the NewStringUTF JNI method
which expects Java's modified UTF-8 (characters outside BMP are represented as surrogates
which are encoded into 3-bytes sequences)

The scanner would find these files/folders but no notifications would be received for them and
their children.
"""

import os
from lib.files import instance_path, wait_file_with_content

# Kanji ftw
DIR_NAME = u'\U00029e3d'.encode('utf8')
FILE_NAME = u'\U00029e38'.encode('utf8')


def put():
    print 'put', instance_path(DIR_NAME, FILE_NAME)
    os.makedirs(instance_path(DIR_NAME).decode('utf8'))
    with open(instance_path(DIR_NAME, FILE_NAME).decode('utf8'), "w") as f:
        f.write("hello")

def get():
    print 'get', instance_path(DIR_NAME, FILE_NAME)
    wait_file_with_content(instance_path(DIR_NAME, FILE_NAME).decode('utf8'), "hello")


spec = {'entries': [put], 'default': get}
