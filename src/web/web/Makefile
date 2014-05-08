SRC=static-src
DEST=static
LESS=less
JS=js
watchman=watchman

.PHONY : all clean watch

FILES_IN_CSS = $(shell find $(SRC)/$(LESS) -name *.less -maxdepth 1)
FILES_OUT_CSS = $(subst static-src,static,$(subst /less/,/css/compiled/,$(FILES_IN_CSS:.less=.css)))
FILES_IN_JS = $(shell find $(SRC)/$(JS) -name *.js -maxdepth 1)
FILES_OUT_JS = $(subst /js/,/js/compiled/,$(subst static-src,static,$(FILES_IN_JS)))

# Compile the final targets
all: $(FILES_OUT_JS) $(FILES_OUT_CSS)

# Destroy the final targets
clean:
	rm -r $(DEST)/css/compiled/
	rm -r $(DEST)/js/compiled/
	mkdir $(DEST)/css/compiled/
	mkdir $(DEST)/js/compiled/

# Watch the filesystem and recompile on file modification
watch:
	$(watchman) watch $(shell pwd)
	$(watchman) -- trigger $(shell pwd) remakejs '*.js' -- python $(shell pwd)/make.py
	$(watchman) -- trigger $(shell pwd) remakeless '*.less' -- python $(shell pwd)/make.py

$(DEST)/css/compiled/%.css: $(SRC)/$(LESS)/%.less
	lessc $? > $@
	minify $@

$(DEST)/js/compiled/%.js: $(SRC)/$(JS)/%.js
	cp $? $@
	uglifyjs -o $@ $@