IMAGE_NAME = aerofs/config
ROOT = buildroot

FILES = $(shell find -L opt -type f)
LICENSING_LIB_DIR = ../../src/licensing

image: $(FILES:%=$(ROOT)/%)
	$(MAKE) -C $(LICENSING_LIB_DIR)
	../../tools/lazy-copy.sh $(LICENSING_LIB_DIR)/build $(ROOT)/sdist
	docker build -t $(IMAGE_NAME) .

# TODO (WW) move everything to the 'root' folder
$(ROOT)/%: %
	mkdir -p $(@D)
	cp $< $@

.PHONY: image
