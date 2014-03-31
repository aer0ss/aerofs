require 'formula'

class Makensis < Formula
  homepage 'http://nsis.sourceforge.net/'
  url 'http://downloads.sourceforge.net/project/nsis/NSIS%202/2.46/nsis-2.46-src.tar.bz2'
  sha1 '2cc9bff130031a0b1d76b01ec0a9136cdf5992ce'

  depends_on 'scons' => :build

  # scons appears to have no builtin way to override the compiler selection,
  # and the only options supported on OS X are 'gcc' and 'g++'.
  # Use the right compiler by forcibly altering the scons config to set these
  def patches; DATA; end

  resource 'nsis' do
    url 'http://downloads.sourceforge.net/project/nsis/NSIS%202/2.46/nsis-2.46.zip'
    sha1 'adeff823a1f8af3c19783700a6b8d9054cf0f3c2'
  end

  def install
    # makensis fails to build under libc++; since it's just a binary with
    # no Homebrew dependencies, we can just use libstdc++
    # https://sourceforge.net/p/nsis/bugs/1085/
    ENV.libstdcxx if ENV.compiler == :clang

    scons = Formula.factory('scons').opt_prefix/'bin/scons'
    system scons, "NSIS_CONFIG_LOG=yes", "makensis"
    bin.install "build/release/makensis/makensis"
    (share/'nsis').install resource('nsis')
  end
end

__END__
diff -ru /SCons/Config/gnu /SCons/Config/gnu
--- nsis-2.46-src-2/SCons/Config/gnu    2009-02-04 16:52:28.000000000 -0800
+++ nsis-2.46-src/SCons/Config/gnu  2014-03-27 12:07:07.000000000 -0700
@@ -32,6 +32,7 @@
 defenv['SUBSYS_CON'] = '-Wl,--subsystem,console'
 defenv['MSVCRT_FLAG'] = ''
 defenv['STDCALL'] = ' __attribute__((__stdcall__))'
+defenv['DEBUG'] = 'YES'

 ### defines

diff -ru /Source/util.h /Source/util.h
--- nsis-2.46-src-2/Source/util.h   2009-03-28 02:47:26.000000000 -0700
+++ nsis-2.46-src/Source/util.h 2014-03-27 10:48:41.000000000 -0700
@@ -17,6 +17,7 @@
 #ifndef _UTIL_H_
 #define _UTIL_H_

+#include <unistd.h>
 #include <string> // for std::string

 #include "boost/scoped_ptr.hpp" // for boost::scoped_ptr

