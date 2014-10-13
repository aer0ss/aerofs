SRC=static-src
DEST=static
LESS=less
JS=js

IS_WATCHMAN=$(shell which watchman)

.PHONY : all clean watch clean-watch clean-dirs

FILES_IN_CSS = $(shell find $(SRC)/$(LESS) -name *.less -maxdepth 1)
FILES_OUT_CSS = $(subst static-src,static,$(subst /less/,/css/compiled/,$(FILES_IN_CSS:.less=.css)))
FILES_IN_JS = $(shell find $(SRC)/$(JS) -name *.js -maxdepth 1)
FILES_OUT_JS = $(subst /js/,/js/compiled/,$(subst static-src,static,$(FILES_IN_JS)))

# Compile the final targets
all: $(FILES_OUT_JS) $(FILES_OUT_CSS)

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
	mkdir $(DEST)/css/compiled/
	mkdir $(DEST)/js/compiled/

# Watch the filesystem and recompile on file modification
watch:
	watchman watch $(shell pwd)
	watchman -- trigger $(shell pwd) remakejs '*.js' -- python $(shell pwd)/make.py
	watchman -- trigger $(shell pwd) remakeless '*.less' -- python $(shell pwd)/make.py

$(DEST)/css/compiled/%.css: $(SRC)/$(LESS)/%.less
	lessc $? > $@
	minify $@
	# move over compiled and minified marketing site css
	if [ "$@" == static/css/compiled/aerofs-marketing.css ] ; then cp static/css/compiled/aerofs-marketing.min.css ../../../web/styles/css/compiled/style.css ; fi

$(DEST)/js/compiled/%.js: $(SRC)/$(JS)/%.js
	cp $? $@
	uglifyjs -o $@ $@
	# move over marketing JS to marketing site
	if [ "$@" == static/js/compiled/recruiting.js ] ; then cp $@ ../../../web/js/recruiting.js ; fi
