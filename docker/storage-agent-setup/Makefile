IMAGE_NAME = aerofs/storage-agent-setup

image: base
	cp -r ../../resource/templates/client/linux/storage-agent/setup_storage_agent.py buildroot/opt/storage-agent-setup/
	docker build -t $(IMAGE_NAME) .

.PHONY: image base lib proto

base: lib
	$(MAKE) -C ../base/python2.7
lib: proto
	$(MAKE) -C ../../src/python-lib
proto:
	../../invoke proto

