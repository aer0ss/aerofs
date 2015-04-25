IMAGE_NAME = aerofs/status
ROOT = buildroot

FILES = $(shell find -L opt -type f)

image: $(FILES:%=$(ROOT)/%)
	docker build -t $(IMAGE_NAME) .

$(ROOT)/%: %
	mkdir -p $(@D)
	cp $< $@

.PHONY: image
