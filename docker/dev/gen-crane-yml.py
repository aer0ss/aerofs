# This script generates the crane.yml file from crane.yml.jinja template

import sys
from os.path import dirname, realpath, join

# Should point to ~/repos/aerofs/docker
base_dir = dirname(dirname(realpath(__file__)))

# Add the loader path to python import search path
sys.path.append(join(base_dir, 'ship/vm/loader/root/'))

from crane_yml import render_crane_yml

with open(join(base_dir, 'crane.yml'), 'wb') as f:
	f.write(render_crane_yml(join(base_dir, 'ship-aerofs/loader/root/crane.yml.jinja')))