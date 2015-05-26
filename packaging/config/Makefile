IMAGE_NAME = aerofs/config
ROOT = buildroot

LICENSING_LIB_DIR = ../../src/licensing

image: $(FILES:%=$(ROOT)/%)
	$(MAKE) -C $(LICENSING_LIB_DIR)
	../../tools/lazy-copy.sh $(LICENSING_LIB_DIR)/build $(ROOT)/sdist
	docker build -t $(IMAGE_NAME) .

.PHONY: image
