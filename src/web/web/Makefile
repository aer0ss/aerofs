SRC = static-src
DEST = static

APP_FOLDERS = $(SRC)/fellowship $(SRC)/saruman $(SRC)/shadowfax $(SRC)/shelob $(SRC)/strider
APP_TARGETS = $(DEST)/fellowship.js $(DEST)/saruman.js $(DEST)/shadowfax.js $(DEST)/shelob.js $(DEST)/strider.js

FILES_IN_LESS = $(wildcard $(SRC)/less/*.less)
FILES_OUT_CSS = $(subst static-src,static,$(subst /less/,/css/compiled/,$(FILES_IN_LESS:.less=.css)))
FILES_OUT_MINIFIED_CSS = $(subst .css,.min.css,$(FILES_OUT_CSS))

FILES_IN_JS = $(wildcard $(SRC)/js/*.js)
FILES_OUT_JS = $(subst /js/,/js/compiled/,$(subst static-src,static,$(FILES_IN_JS)))


all: install $(FILES_OUT_JS) $(FILES_OUT_MINIFIED_CSS) $(APP_TARGETS)

clean:
ifneq ($(shell which watchman),)
	watchman trigger-del $(shell pwd) remakejs
	watchman trigger-del $(shell pwd) remakeless
endif

	rm -rf $(DEST)/css/compiled/
	rm -rf $(DEST)/js/compiled/
	rm -f $(DEST)/*.js


# npm install ALWAYS refreshes make targets
install:
	npm install


setup_test:
	npm install
	( cd ../jstest && npm install )

test: setup_test
	../jstest/run_tests.sh


# The list to run the jshint on:
FILES   += $(SRC)/js/recruiting.js
FILES   += $(SRC)/js/csrf.js
# FIXME: All js files should be jshint'ed, but they are not compliant;
# example:
#  FILES ++ $(SRC)/js/aerofs.js

jshint:
	for t in $(FILES) ; do \
		jshint --config $(shell pwd)/jshintrc $$t ; \
	done

watch:
	watchman watch $(shell pwd)
	for t in $(APP_FOLDERS) ; do \
		watchman -- trigger $$t remakejs_$$t '*.js' -- python $(shell pwd)/make.py $(shell pwd) ; \
		watchman -- trigger $$t remakeless_$$t '*.less' -- python $(shell pwd)/make.py $(shell pwd) ; \
	done


$(DEST)/css/compiled/%.css: $(SRC)/less/%.less
	@mkdir -p $(@D)
	node_modules/.bin/lessc $< > $@

$(DEST)/css/compiled/%.min.css: $(DEST)/css/compiled/%.css
	node_modules/.bin/minify $<

$(DEST)/js/compiled/%.js: $(SRC)/js/%.js
	@mkdir -p $(@D)
	node_modules/.bin/uglifyjs -o $@ $<


SHELOB_FILES = $(wildcard $(SRC)/shelob/*.js)
SHELOB_CONFIG_FILE = $(SRC)/shelob/config/config_private.js
SHELOB_DEPENDENCIES = $(SRC)/shadowfax/directives.js $(SRC)/shadowfax/filters.js
$(DEST)/shelob.js: $(SHELOB_FILES) $(SHELOB_CONFIG_FILE) $(SHELOB_DEPENDENCIES)
	cat $^ > $@

FELLOWSHIP_FILES = $(wildcard $(SRC)/fellowship/*.js)
$(DEST)/fellowship.js: $(FELLOWSHIP_FILES)
	cat $^ > $@

SARUMAN_FILES = $(wildcard $(SRC)/saruman/*.js)
$(DEST)/saruman.js: $(SARUMAN_FILES)
	cat $^ > $@

SHADOWFAX_FILES = $(wildcard $(SRC)/shadowfax/*.js)
SHADOWFAX_DEPENDENCIES = $(SRC)/shelob/services.js $(SRC)/shelob/config/config_private.js
$(DEST)/shadowfax.js: $(SHADOWFAX_FILES) $(SHADOWFAX_DEPENDENCIES)
	cat $^ > $@

STRIDER_FILES = $(wildcard $(SRC)/strider/*.js)
STRIDER_DEPENDENCIES = $(SRC)/shadowfax/filters.js
$(DEST)/strider.js: $(STRIDER_FILES) $(STRIDER_DEPENDENCIES)
	cat $^ > $@
