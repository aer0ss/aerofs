require 'formula'

class Scons < Formula
  url 'http://downloads.sourceforge.net/project/scons/scons/2.0.1/scons-2.0.1.tar.gz'
  homepage 'http://www.scons.org'
  sha256 '0a8151da41c4a26c776c84f44f747ce03e093d43be3e83b38c14a76ab3256762'

  def install
    man1.install gzip('scons-time.1', 'scons.1', 'sconsign.1')
    system "/usr/bin/python", "setup.py", "install",
             "--prefix=#{prefix}",
             "--standalone-lib",
             "--no-version-script", "--no-install-man"
  end
end
