IMAGE_NAME = aerofs/auditor
ROOT = buildroot/opt/auditor

# PREDOCKER
image: $(ROOT)/logback.xml $(ROOT)/auditor.properties
	gradle --daemon dist
	../../tools/lazy-copy.sh ../../out.gradle/auditor/dist $(ROOT)
	docker build -t $(IMAGE_NAME) .

$(ROOT)/%: resources/%
	mkdir -p $(@D)
	cp $< $@
