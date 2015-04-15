# Use a non-default dist dir to avoid interference with python-buildpackage.sh
SRC = $(shell find -L . -type f)

DIST_DIR = build
IGNORE = %.pyc ./MANIFEST ./$(DIST_DIR)/% ./dist/% ./test_all.py ./unittests/% ./Makefile ./.%
SRC := $(filter-out $(IGNORE), $(SRC))

DIST_FILE = $(DIST_DIR)/$(shell python setup.py --name)-$(shell python setup.py --version).tar.gz

$(DIST_FILE): $(SRC)
	python setup.py sdist --formats=gztar --dist-dir $(DIST_DIR)

clean:
	rm -rf $(DIST_DIR)

.PHONY: clean
