# Usage:
# brew install --HEAD tools/protobuf-objc.rb

require 'formula'

# Documentation: https://github.com/mxcl/homebrew/wiki/Formula-Cookbook

class ProtobufObjc < Formula
  # homepage 'http://github.com/booyah/protobuf-objc'
  # head 'git://github.com/booyah/protobuf-objc.git'
  homepage 'http://github.com/aerofs/protobuf-objc'
  head 'git://github.com/aerofs/protobuf-objc.git'
  md5 ''

  depends_on 'protobuf'
  depends_on 'autoconf' => :build
  depends_on 'automake' => :build

  def install
    system "./autogen.sh"
    system "./configure",
      "--disable-dependency-tracking",
      "--prefix=#{prefix}"
    system "make install"
  end

  def test
    # This test will fail and we won't accept that! It's enough to just replace
    # "false" with the main program this formula installs, but it'd be nice if you
    # were more thorough. Run the test with `brew test protobuf`.
    system "false"
  end
end
