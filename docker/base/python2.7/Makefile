IMAGE_NAME = aerofs/base.python2.7

PYTHON_LIB_DIR = ../../../src/python-lib
RSYNC = rsync -a --delete --delete-excluded --exclude '*.pyc' --exclude '.*'

image:
	mkdir -p buildroot
	$(RSYNC) --exclude build --exclude unittests $(PYTHON_LIB_DIR) buildroot/
	docker build -t $(IMAGE_NAME) .

.PHONY: image
