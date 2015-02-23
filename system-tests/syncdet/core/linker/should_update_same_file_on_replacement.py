"""
When editors like vim and MS Word save a document, they first write the new
content to a temporary file, delete the original file, and then move the
temporary file to the original. In this situation, AeroFS on other peers should
not delete the original object and create a new one. Instead, the content of the
original should be updated.
"""
import os
import time
from syncdet import case
import common

def replace(path, content, r):
    # create a temporary file
    pathTemp = os.path.join(case.user_data_folder_path(), "tmp")
    with open(pathTemp, 'w') as f: f.write(content)

    # delete the original
    os.remove(path)

    # simulate delays in replacing the file
    time.sleep(0.1)

    # replace with the temp file
    os.rename(pathTemp, path)

spec = common.replace_test(replace)
