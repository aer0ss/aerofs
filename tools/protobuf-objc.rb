require 'formula'

class ProtobufObjc < Formula
  depends_on 'protobuf'
  depends_on 'automake'
  depends_on :libtool

  head 'https://github.com/aerofs/protobuf-objc.git'
  homepage 'https://github.com/aerofs/protobuf-objc.git'

  def install
    system "./autogen.sh"
    system "./configure", "--prefix=#{prefix}"
    system "make install"
  end

  def test
    system "which protoc-gen-objc"
  end
end
