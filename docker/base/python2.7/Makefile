IMAGE_NAME = aerofs/base.python2.7

PYTHON_LIB_DIR = ../../../src/python-lib

image:
	$(MAKE) -C $(PYTHON_LIB_DIR)
	../../../tools/lazy-copy.sh "$(PYTHON_LIB_DIR)/build" "buildroot/sdist"
	docker build -t $(IMAGE_NAME) .
