
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


Compiling Less and JS:
======================
1. Install npm packages for compiling and minifying.
$ npm install -g lessc minifier uglify-js

2. If you're going to be modifying LESS or JS more than once, install watchman to enable automatic compilation on save. 

$ brew watchman

3. For a one-off change, run:
$ cd src/web/web
$ make clean && make

4. If you have watchman, run:
$ make watch

Watchman will automagically (and silently) re-run make in the background every time the source files change.

By default, compiled static files are no longer included in source control. Thus, you will need to run make (or have make watch running) every time LESS or JS files are changed.

Working on CSS/Less/JS/Mako:
============================
Less files live in static-src/less. If the Less file is one that you want to get turned into a CSS file with the same name, put it in static-src/less. If you want the Less file to be included by other Less files, put it in static-src/less/includes. Less dependencies that we didn't write (like Bootstrap) also live here since they're included by other files.

Third-party CSS files live in static/css. Only compiled files live in static/css/compiled; do not edit these--your edits will be overwritten.

Excepting shelob-related files, JS files written or edited by us live in static-src/js/. Third-party JS files live in static/js. Files in static/js/compiled are compiled; please don't edit them.

Currently, to get Mako changes to show up on https://unified.syncfs.com you have to run `sudo service uwsgi stop && sudo service uwsgi start` on Vagrant. Sorry about that. There might be a Pyramid mode that will help the site notice when template files change and refresh itself for you. TODO: Further research required.

Many places in the codebase currently have inline styles or stylesheets. This is not great. Please move these out into their own Less files, then include the compiled CSS equivalents (`filename.min.css`) in the page(s) that need them. (Less is a superset of CSS, so you can do this without changing any of the markup--though it's probably worthwhile to sanity-check it at the same time as the move.)

In general, try to avoid repeating yourself in styling code--use more generic selectors and reuse styles whenever you can. Read the Bootstrap docs--and make sure you're looking at the correct Bootstrap version. Ask Karen if you're not sure what to do.

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

Make sure that all static files are compiled. (See "Compiling Less and JS")
$ cd src/web/web/
$ make clean && make

And now, to actually update the files stored in S3:
$ cd ../  # src/web/
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
2. Copy the entire less folder from the downloaded zip to src/web/web/static-src/less/includes/bootstrap
3. Add the following _to the end_ of variables.less:
    @import "../aerofs-variables.less";
4. In src/web/web, run:
$ make clean && make
