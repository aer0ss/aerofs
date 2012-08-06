#!/bin/bash

# Need to run proto commands, etc.
echo "Preparing test environment..."
cd $(dirname $0)/../..
make clean debs 1>/dev/null 2>/dev/null

# Need to install python packages.
cd python
for package in $(ls)
do
    cd $package
    python setup.py develop 1>/dev/null 2>/dev/null
    cd ..
done
cd ..

# Only inception tests exist at this time. Add more tests here.
echo "Perform testing (might take a few seconds)..."
python python/inception/inception/tests/tests.py
