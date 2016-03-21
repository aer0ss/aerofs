import zipfile
import os
from aerofs_common.configuration import Configuration

CONFIG_DIR = "saconfig"
SITE_CONFIG_FILENAME = "site-config.properties"
SETUP_PROPERTIES_FILENAME = "setup.properties"
BUNDLE_NAME = "storage-config"


def bundle_creator(token, config_addr, properties, outputdir):
    ret = StorageAgentConfig()
    properties["token"] = token
    ret.props = properties
    ret.site_config_props = None
    ret.output_dir = outputdir
    ret.load_config(config_addr)
    return ret


def bundle_reader(bundle_path, outputdir):
    ret = StorageAgentConfig()
    ret.bundle = bundle_path
    ret.output_dir = outputdir
    return ret


class StorageAgentConfig(object):
    def __init__(self):
        self.site_config_props = None
        self.props = None
        self.bundle = None
        self.output_dir = None

    def load_config(self, config_addr):
        assert config_addr.startswith("http://"), "only insecure config loading is supported"
        config = Configuration(config_addr).client_properties()
        self.site_config_props = {}
        for k, v in config.iteritems():
            if k.startswith("config.loader") or k == "base.sp.url":
                self.site_config_props[k] = v

    def write_files(self):
        if not self.props or not self.site_config_props:
            raise Exception("cannot write files without properties to write")

        abs_directory = os.path.join(self.output_dir, CONFIG_DIR)
        if not os.path.exists(abs_directory):
            os.makedirs(abs_directory)

        site_config_relpath = os.path.join(CONFIG_DIR, SITE_CONFIG_FILENAME)
        site_config_abspath = os.path.join(abs_directory, SITE_CONFIG_FILENAME)
        with open(site_config_abspath, 'w') as f:
            for k, v in self.site_config_props.iteritems():
                f.write("{}={}\n".format(k, v))

        setup_props_relpath = os.path.join(CONFIG_DIR, SETUP_PROPERTIES_FILENAME)
        setup_props_abspath = os.path.join(abs_directory, SETUP_PROPERTIES_FILENAME)
        with open(setup_props_abspath, 'w') as f:
            for k, v in self.props.iteritems():
                f.write("{}={}\n".format(k, v))

        bundle_path = os.path.join(self.output_dir, BUNDLE_NAME)
        with zipfile.ZipFile(bundle_path, mode='w') as z:
            z.write(filename=site_config_abspath, arcname=site_config_relpath)
            z.write(filename=setup_props_abspath, arcname=setup_props_relpath)

        return bundle_path

    def read_bundle(self):
        if not self.bundle:
            raise Exception("cannot read bundle without bundle to read")

        with open(self.bundle, 'rb') as f:
            z = zipfile.ZipFile(f)
            site_config_relpath = os.path.join(CONFIG_DIR, SITE_CONFIG_FILENAME)
            setup_props_relpath = os.path.join(CONFIG_DIR, SETUP_PROPERTIES_FILENAME)
            z.extract(site_config_relpath, self.output_dir)
            z.extract(setup_props_relpath, self.output_dir)

        site_config_path = os.path.join(self.output_dir, CONFIG_DIR, SITE_CONFIG_FILENAME)
        setup_props_path = os.path.join(self.output_dir, CONFIG_DIR, SETUP_PROPERTIES_FILENAME)
        site_config = dict(line.strip().split('=', 1) for line in open(site_config_path) if line.strip())
        setup_props = dict(line.strip().split('=', 1) for line in open(setup_props_path) if line.strip())
        # TODO (RD) should this destroy the zip?
        return setup_props, site_config
