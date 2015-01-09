
.PHONY: all clean

all:
	$(MAKE) -C ../ship/vm/loader
	$(MAKE) -C loader
	./build.sh

clean:
	rm -rf build
	docker rm -f aerofs-preload-registry
