
  @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
  @ Please read README.security.txt before contributing any Web code! @
  @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@


Run AeroFS website in a virtual environment
================================================

# First, install virtualenv
sudo easy_install pip
sudo pip install virtualenv

# Set up virtualenv and install packages.
# NOTE: requests is pinned because version 1.0.0 is not backwards compatible
# TODO: (PH) fix code to use requests >=1.0.0
virtualenv ~/env

# Then, install AeroFS python library:
cd ~/repos/aerofs/src/python-lib && ~/env/bin/python setup.py develop

# And then, install AeroFS website:
cd ~/repos/aerofs/src/web && ~/env/bin/python setup.py develop

# Finally, run AeroFS website!
export STRIPE_PUBLISHABLE_KEY=pk_test_nlFBUMTVShdEAASKB0nZm6xf
export STRIPE_SECRET_KEY=sk_test_lqV5voHmrJZLom3iybJFSVqK
~/env/bin/pserve development.ini

# To run test cases:
cd ~/repos/aerofs/src/web && ~/env/bin/python setup.py test -q

Creating new modules for AeroFS website
================================================
- Create module in modules folder (copy an existing module as a reference, edit
  as needed)
- Add module name to __all__ in modules/__init__.py
- If the module has templates, make sure to put them in a directory named
  "templates" inside the module folder

Dependencies:
================================================
When adding new dependencies to the pyramid package (things that would be
installed via pip or easy_install) make sure you add them to setup.py!
Otherwise the web package will break for everyone else who uses it.

Updating Cloudfront:
================================================
We use Cloudfront for our static assets, so when our local copies are changed
they need to be updated in cloudfront too when we want to serve them in production.
Anything stored in web/static is served from Cloudfront.

To get setup to update Cloudfront, get credentials to the aerofs.admin_panel bucket
on S3 then run the following commands to configure your S3 environment:
# remove the devel tag once v1.1.0 or higher of s3cmd is released
$ brew install --devel s3cmd
$ s3cmd --configure
[enter your credentials, skip entering an encryption password or a gpg binary]

And now, to actually update the files stored in S3:
$ cd src/web
$ ./update_cloudfront

Updating Libraries:
==================
If you decide to download a new version of bootstrap.js note that the following modification
needs to be applied under the Typeahead object for the function lookup:
if (this.query === "") {
    items = this.source;
    if (items.length > 0) {
        return this.render(items.slice(0, this.options.items)).show();
    }
}
