
  @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@
  @ Please read README.security.txt before contributing any Web code! @
  @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@


Run AeroFS website in a virtual environment
================================================

Set up your system using development/setup.sh (run without args for usage
information). After setup has completed, run the web server using
development/run.sh.

N.B. the process needs write permissions to /var/log/web/web.log and
/var/log is owned by root by default on OSX. Create that directory
and chown it before running run.sh

Known issues:
    - Sign in does not work when running in prod mode (cookie issue).

# To run test cases:
cd ~/repos/aerofs/src/web && ~/env/bin/python test_all.py

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

Updating Bootstrap:
==================
A. Update JS and images
----
1. Download prebuilt Bootstrap from http://twitter.github.com/bootstrap/assets/bootstrap.zip
2. Copy bootstrap.min.js and files under img to src/web/web/static/{js,img}

B. Update LESS and CSS
----
1. Download the latest release at https://github.com/twitter/bootstrap/zipball/master
2. Copy the entire less folder from the downloaded zip to src/web/web/less/bootstrap
3. Add the following _to the end_ of variables.less:
    @import "../aerofs-variables.less";
4. Recompile bootstrap.less, responsive.less, and aerofs.less into css files
   using tools like http://incident57.com/less/.
