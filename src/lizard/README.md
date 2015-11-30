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

On some versions of OS X, compiling the module pygpgme fails because clang
won't search the standard include paths (`/usr/local/include`).

If you're seeing error messages about include files not found while running
`env/bin/pip install -r requirements-exact.txt`, try the following:

```
export CFLAGS="${CFLAGS} -I/usr/local/include"
export LDFLAGS="${LDFLAGS} -L/usr/local/lib"
env/bin/pip install -r requirements-exact.txt
```

On el cap, openssl may also be borked, in this case add your openssl path:


```
export CFLAGS="${CFLAGS} -I/usr/local/Cellar/openssl/1.0.2d_1/include"
export LDFLAGS="${LDFLAGS} -L/usr/local/Cellar/openssl/1.0.2d_1/lib"
env/bin/pip install -r requirements-exact.txt
```
