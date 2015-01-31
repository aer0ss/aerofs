IMAGE_NAME = aerofs/auditor
ROOT = buildroot/opt/auditor

# PREDOCKER
image: $(ROOT)/auditor.yml
	gradle --daemon dist
	../../tools/lazy-copy.sh ../../out.gradle/auditor/dist $(ROOT)
	docker build -t $(IMAGE_NAME) .

$(ROOT)/%: src/dist/%
	mkdir -p $(@D)
	cp $< $@
