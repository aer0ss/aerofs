all:

osx: clean
	( cd build && qmake CONFIG+=release ../launcher.pro && make )

linux32:
	make -C ../../tools/build/linux32 COMMAND='make -C /mnt/repos/aerofs/src/launcher linux32-emulated'

linux32-emulated: clean
	( cd build && /usr/bin/linux32 qmake CONFIG+=release ../launcher.pro && make )

linux64:
	make -C ../../tools/build/linux64 COMMAND='make -C /mnt/repos/aerofs/src/launcher linux64-emulated'

linux64-emulated: clean
	( cd build && qmake -spec linux-g++-64 CONFIG+=release ../launcher.pro && make )


clean:
	rm -rf build
	mkdir -p build
