require 'rubygems'
require 'bundler'
require 'net/http'
require 'net/https'
require 'openssl'
require 'webrick/https'
require 'sinatra'
require 'sinatra/streaming'
require 'parseconfig'

OpenSSL::SSL::VERIFY_PEER = OpenSSL::SSL::VERIFY_NONE

class ManInTheMiddle < Sinatra::Base
    helpers Sinatra::Streaming

    get '/*' do |filename|
        config = ParseConfig.new('/opt/middleman/middleman.conf')
        host = config["target"]
        url = URI("https://#{host}/#{filename}")

        stream do |out|
            # Setup the https object
            https = Net::HTTP.new(url.host, url.port)
            https.use_ssl = true

            # Perform the actual request
            https.request_get url.path do |response|
                # Read the body in chunks and send them to the client
                # as soon as we receive them
                response.read_body { |data| out << data }
            end
        end
    end

end

logger = WEBrick::Log::new('/opt/middleman/middleman.log', WEBrick::Log::DEBUG)

webrick_options = {
    :Port               => 443,
    :Logger             => logger,
    :AccessLog          => [ [ logger, WEBrick::AccessLog::COMBINED_LOG_FORMAT ] ],
    :DocumentRoot       => "/ruby/htdocs",
    :SSLEnable          => true,
    :SSLVerifyClient    => OpenSSL::SSL::VERIFY_NONE,
    :SSLCertificate     => OpenSSL::X509::Certificate.new(  File.open('/opt/middleman/fakes3.crt').read),
    :SSLPrivateKey      => OpenSSL::PKey::RSA.new(          File.open('/opt/middleman/fakes3.key').read),
    :SSLCertName        => [ [ "CN",WEBrick::Utils::getservername ] ]
}

Rack::Handler::WEBrick.run ManInTheMiddle, webrick_options
