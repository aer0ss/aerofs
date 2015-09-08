all:

osx: clean
	( cd build && qmake -spec unsupported/macx-clang ../aerofsd.pro && make )
	cp build/libaerofsd.1.0.0.dylib ../../resource/client/osx/libaerofsd.dylib

linux32:
	make -C ../../tools/build/linux32 COMMAND='make -C /mnt/repos/aerofs/src/swiglibs linux32-emulated'

linux32-emulated: clean
	( cd build && /usr/bin/linux32 qmake ../aerofsd.pro && make )
	cp build/libaerofsd.so.1.0.0 /mnt/repos/aerofs/resource/client/linux/i386/libaerofsd.so

linux64:
	make -C ../../tools/build/linux64 COMMAND='make -C /mnt/repos/aerofs/src/swiglibs linux64-emulated'

linux64-emulated: clean
	( cd build && qmake ../aerofsd.pro && make )
	cp build/libaerofsd.so.1.0.0 /mnt/repos/aerofs/resource/client/linux/amd64/libaerofsd.so


clean:
	rm -rf build
	mkdir -p build
