GIT_ROOT = $(shell git rev-parse --show-toplevel)

# Use a non-default dist dir to avoid interference with python-buildpackage.sh
SRC = $(shell find -L . -type f)

DIST_DIR = build
IGNORE = %.pyc ./MANIFEST ./$(DIST_DIR)/% ./dist/% ./test_all.py ./tests/% ./Makefile ./.%
SRC := $(filter-out $(IGNORE), $(SRC))

DIST_FILE = $(DIST_DIR)/$(shell python setup.py --name)-$(shell python setup.py --version).tar.gz

$(DIST_FILE): $(SRC) proto
	python setup.py sdist --formats=gztar --dist-dir $(DIST_DIR)

proto:
	make -C $(GIT_ROOT) proto

clean:
	rm -rf $(DIST_DIR)

.PHONY: clean proto
