To rebuild the packages:

./apk.sh dmg-hfplus
./apk.sh gcab
./apk.sh msitools gcab-dev

NB: remove APKINDEX.tar.gz before you build as a package signing key is generated
when the build container is first created and the build tool will not update the
pckage index and the index unless the signing keys match.

This means that you can't just rebuild one package without rebuilding them all.
