include ../../Makefile.base

IMAGE_NAME = aerofs/base.python2.7

PYTHON_LIB_DEST = buildroot/python-lib/
RSYNC += --exclude build --exclude unittests

buildroot: $(PYTHON_LIB_SRC)
	mkdir -p $(PYTHON_LIB_DEST)
	$(RSYNC) --link-dest=$(PYTHON_LIB_SRC) $(PYTHON_LIB_SRC) $(PYTHON_LIB_DEST)
