"""
Stress-test the aliasing subsystem by creating identical directory trees on all
actors. Exercise aliasing small hierarchies of large files.
"""

#TODO: creating large files in a syncdet test is not a good idea
# a better idea would be to pre-gen such files and rsync them in
# a special location as part of syncdet deploy to allow test to
# copy/move them around as required
