Getting Started

```bash
cd ~/repos/aerofs/src/lizard
virtualenv env
env/bin/pip install -r requirements-exact.txt
env/bin/pip install --editable ../licensing
./rundebug.py
```

To run the lizard administrative interface locally, do a

```
./rundebug.py internal
```

Now you should be able to hit the instance on localhost:4444 in your browser.


Note
====

On some versions of OS X, compiling the module pygpgme fails because clang won't search the standard include paths (/usr/local/include)

If you're seeing error messages about include files not found while running `env/bin/pip install -r requirements-exact.txt`, try the following:

	$ export CFLAGS="-I/usr/local/include"
	$ export LDFLAGS="-L/usr/local/lib"
	$ env/bin/pip install -r requirements-exact.txt