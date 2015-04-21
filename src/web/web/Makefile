SRC=static-src
DEST=static
DOCS_DEST=../../../docs/user-docs/api
LESS=less
JS=js

IS_WATCHMAN=$(shell which watchman)

.PHONY : all clean watch clean-watch clean-dirs

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
all: $(FILES_OUT_JS) $(FILES_OUT_MINIFIED_CSS) $(EXTERNAL_ASSETS_OUT)

# Destroy the final targets
ifneq ($(IS_WATCHMAN),)
clean: clean-watch clean-dirs
else
clean: clean-dirs
endif

clean-watch:
	watchman trigger-del $(shell pwd) remakejs
	watchman trigger-del $(shell pwd) remakeless

clean-dirs:
	rm -rf $(DEST)/css/compiled/
	rm -rf $(DEST)/js/compiled/
	rm -rf $(DOCS_DEST)/css/compiled/
	rm -f $(EXTERNAL_ASSETS_OUT)
	mkdir $(DEST)/css/compiled/
	mkdir $(DEST)/js/compiled/
	mkdir $(DOCS_DEST)/css/compiled/

# Watch the filesystem and recompile on file modification
watch:
	watchman watch $(shell pwd)
	watchman -- trigger $(shell pwd) remakejs '*.js' -- python $(shell pwd)/make.py
	watchman -- trigger $(shell pwd) remakeless '*.less' -- python $(shell pwd)/make.py

# Special rules for things which get placed outside of the web project
# API docs site files
$(DOCS_DEST)/css/compiled/aerofs.min.css: $(DEST)/css/compiled/aerofs.min.css
	mkdir -p $(@D)
	cp $? $@
$(DOCS_DEST)/css/compiled/api.min.css: $(DEST)/css/compiled/api.min.css
	mkdir -p $(@D)
	cp $? $@

# .min.css files come from .css files...
$(DEST)/css/compiled/%.min.css: $(DEST)/css/compiled/%.css
	minify $?

# and .css files come from .less files.
$(DEST)/css/compiled/%.css: $(SRC)/$(LESS)/%.less
	mkdir -p $(@D)
	lessc $? > $@

# .js files in the compiled folder come from .js files in the source folder
$(DEST)/js/compiled/%.js: $(SRC)/$(JS)/%.js
	mkdir -p $(@D)
	uglifyjs -o $@ $?
