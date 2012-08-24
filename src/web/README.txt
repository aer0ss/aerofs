web README

Run AeroFS website in a virtual environment
================================================
First install virtualenv, then:

$ virtualenv ~/env
$ ~/env/bin/pip install protobuf pycrypto

Now, install AeroFS python library:
$ cd ~/repos/aerofs/java/aerofs.python-lib
$ ~/env/bin/python setup.py develop

Finally, install and run AeroFS website:
$ cd ~/repos/aerofs/java/web
$ ~/env/bin/python setup.py develop
$ ~/env/bin/pserve development.ini


Creating new modules for AeroFS website
================================================
- Create module in modules folder (copy an existing module as a reference, edit
  as needed)
- Add module name to __all__ in modules/__init__.py
- If the module has templates, make sure to put them in a directory named
  "templates" inside the module folder

Localization:
================================================
To make our application localizable, all user facing strings must be run
through the _ function (gettext will scan for calls to the _ function to
extract messages that need to be localized). To make this easier, we have
combined the localization function with the translation function, so calling
_ on your user-facing strings (and using it to do parameter substitution) tags
them for future translation and allows them to be translated once translations
for them are added. Here are some examples of importing _ and using it:

# import line (only needed once at the beginning of any function doing translation)
_ = request.translate

# the string here is tagged for translation and translated if possible
message = _("This is a message.")

# here name is substituted in for '${user} based on the given mapping dictionary
message = _("This is a message, ${user}", {'user': name})

In both cases above message now holds a translated and parameter-substituted
version of the original string passed in to _

Localization Management Commands:
    Extract Messages: ~/env/bin/python setup.py extract_messages
        Scans through all the source code in the different web packages,
        picks out any strings tagged/translated (strings with _ called
        on them as described above) and saves them to a .pot file in locale/
    Generate Language Catalog: ~/env/bin/python setup.py init_catalog -l LOCALE
        Generates an empty .po translation file and other locale-related files
        in locale/LOCALE, where LOCALE is any standard locale (eg. 'en' for
        English or 'en_US' for US English). The .po file can be filled in by
        a translater with correct translations after being generated.
    Update Language Catalog: ~/env/bin/python setup.py update_catalog
        Merges new messages from the .pot file into existing .po files. So when
        new strings are added to the application source code, running
        extract_messages and then update_messages will propagate those new
        strings into all of the .po files without changing the translations that
        are already there.
    Compile Language Catalogs: ~/env/bin/python setup.py compile_catalog
        Compiles all .po files in locale folders to .mo (machine object) files
        so that Pyramid can use them at runtime.

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
