SRC=static-src
DEST=static
DOCS_DEST=../../../docs/user-docs/api
LESS=less
JS=js

IS_WATCHMAN=$(shell which watchman)

.PHONY : all clean install watch clean-watch clean-dirs setup_test test cache jshint
APP_TARGETS = $(DEST)/shadowfax.js $(DEST)/strider.js $(DEST)/shelob.js $(DEST)/fellowship.js $(DEST)/saruman.js

# input less files
FILES_IN_LESS = $(wildcard $(SRC)/$(LESS)/*.less)
# intermediate css files - compiled from less, but not yet minified
FILES_OUT_CSS = $(subst static-src,static,$(subst /less/,/css/compiled/,$(FILES_IN_LESS:.less=.css)))
# minified css files, suitable for production use
FILES_OUT_MINIFIED_CSS = $(subst .css,.min.css,$(FILES_OUT_CSS))

# input js files
FILES_IN_JS = $(wildcard $(SRC)/$(JS)/*.js)
# uglified js files, smaller, faster loading
FILES_OUT_JS = $(subst /js/,/js/compiled/,$(subst static-src,static,$(FILES_IN_JS)))

# A couple resources go in external folders, with special file paths.
EXTERNAL_ASSETS_OUT = $(DOCS_DEST)/css/compiled/aerofs.min.css \
	$(DOCS_DEST)/css/compiled/api.min.css

# Compile the final targets
all: $(FILES_OUT_JS) $(FILES_OUT_MINIFIED_CSS) $(EXTERNAL_ASSETS_OUT) $(APP_TARGETS)

# Destroy the final targets
ifneq ($(IS_WATCHMAN),)
clean: clean-watch clean-dirs
else
clean: clean-dirs
endif

install:
	npm install

clean-watch:
	watchman trigger-del $(shell pwd) remakejs
	watchman trigger-del $(shell pwd) remakeless

clean-dirs:
	rm -rf $(DEST)/css/compiled/
	rm -rf node_modules/
	rm -rf $(DEST)/js/compiled/
	rm -rf $(DOCS_DEST)/css/compiled/
	rm -f $(EXTERNAL_ASSETS_OUT)
	rm -f $(DEST)/*.js
	mkdir $(DEST)/css/compiled/
	mkdir $(DEST)/js/compiled/
	mkdir $(DOCS_DEST)/css/compiled/

.ONESHELL:
# set up npm install to run the tests
setup_test:
	npm install
	cd ../jstest/shelob && npm install

.ONESHELL:
# run karma tests
test:
	cd ../jstest/shelob && ./node_modules/karma/bin/karma start karma.conf.unit.js && ./node_modules/karma/bin/karma start karma.conf.e2e.js
 
# Watch the filesystem and recompile on file modification
TARGS = $(SRC)/shelob $(SRC)/saruman $(SRC)/fellowship $(SRC)/strider $(SRC)/shadowfax

# The list to run the jshint on:
FILES   += $(SRC)/js/recruiting.js
FILES   += $(SRC)/js/csrf.js
# FIXME: All js files should be jshint'ed, but they are not compliant;
# example:
#  FILES ++ $(SRC)/js/aerofs.js

jshint:
	for t in $(FILES) ; \
	do \
		jshint --config $(PWD)/jshintrc $$t ; \
	done

watch:
	watchman watch $(shell pwd)
	for t in $(TARGS) ; \
	 do \
	 	watchman -- trigger $$t remakejs_$$t '*.js' -- python $(shell pwd)/make.py $(shell pwd) ; \
	 	watchman -- trigger $$t remakeless_$$t '*.less' -- python $(shell pwd)/make.py $(shell pwd) ; \
	 done



# Special rules for things which get placed outside of the web project
# API docs site files
$(DOCS_DEST)/css/compiled/aerofs.min.css: $(DEST)/css/compiled/aerofs.min.css
	mkdir -p $(@D)
	cp $? $@
$(DOCS_DEST)/css/compiled/api.min.css: $(DEST)/css/compiled/api.min.css
	mkdir -p $(@D)
	cp $? $@

# .min.css files come from .css files...
$(DEST)/css/compiled/%.min.css: $(DEST)/css/compiled/%.css install
	node_modules/.bin/minify $<

# and .css files come from .less files.
$(DEST)/css/compiled/%.css: $(SRC)/$(LESS)/%.less install
	mkdir -p $(@D)
	node_modules/.bin/lessc $< > $@

# .js files in the compiled folder come from .js files in the source folder
$(DEST)/js/compiled/%.js: $(SRC)/$(JS)/%.js install
	mkdir -p $(@D)
	node_modules/.bin/uglifyjs -o $@ $<

#concatenation, watch the changes in $(DEST)/js/compiled/%.js turn it into bundled.js

# Copy files from static-src to static.
# $(DEST)/%: $(SRC)/%
# 	mkdir -p $(@D)
# 	cp -R $< $@

FOLDERS_SRC=$(SRC)/shelob \
	$(SRC)/strider \
	$(SRC)/fellowship \
	$(SRC)/saruman \
	$(SRC)/shadowfax

FILES_SRC=$(shell find -L $(FOLDERS_SRC) -type f)

$(DEST)/shelob.js: $(SRC)/shelob/js/app.js \
	$(SRC)/shelob/js/controllers.js \
	$(SRC)/shelob/js/directives.js \
	$(SRC)/shelob/js/filters.js \
	$(SRC)/shelob/js/services.js \
	$(SRC)/shelob/js/config/config_private.js
	cat $^ > $@

$(DEST)/fellowship.js: $(SRC)/fellowship/app.js \
	$(SRC)/fellowship/controllers.js \
	$(SRC)/fellowship/directives.js \
	$(SRC)/fellowship/filters.js
	cat $^ > $@

$(DEST)/saruman.js: $(SRC)/saruman/app.js \
	$(SRC)/saruman/controllers.js \
	$(SRC)/saruman/directives.js \
	$(SRC)/saruman/filters.js
	cat $^ > $@

$(DEST)/shadowfax.js: $(SRC)/shadowfax/app.js \
	$(SRC)/shadowfax/controllers.js \
	$(SRC)/shadowfax/pagination.js \
	$(SRC)/shadowfax/filters.js
	cat $^ > $@

$(DEST)/strider.js: $(SRC)/strider/app.js \
	$(SRC)/strider/controllers.js \
	$(SRC)/shadowfax/filters.js \
	$(SRC)/strider/directives.js
	cat $^ > $@
