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

Now you should be able to hit the instance on localhost:5000 in your browser.
