require 'rubygems'
require 'bundler'
require 'open-uri'
require 'openssl'
require 'webrick/https'
require 'sinatra'
require "parseconfig"


OpenSSL::SSL::VERIFY_PEER = OpenSSL::SSL::VERIFY_NONE

class ManInTheMiddle < Sinatra::Base
    get '/*' do |filename|
        config = ParseConfig.new('/opt/middleman/middleman.conf')
	url = config["target"]
        open("https://#{url}/#{filename}").read
    end
end

webrick_options = {
	:Port               => 443,
	:Logger             => WEBrick::Log::new($stderr, WEBrick::Log::DEBUG),
	:DocumentRoot       => "/ruby/htdocs",
	:SSLEnable          => true,
	:SSLVerifyClient    => OpenSSL::SSL::VERIFY_NONE,
	:SSLCertificate     => OpenSSL::X509::Certificate.new(  File.open("/opt/middleman/myssl.crt").read),
	:SSLPrivateKey      => OpenSSL::PKey::RSA.new(          File.open("/opt/middleman/myssl.key").read),
	:SSLCertName        => [ [ "CN",WEBrick::Utils::getservername ] ]
}

Rack::Handler::WEBrick.run ManInTheMiddle, webrick_options
