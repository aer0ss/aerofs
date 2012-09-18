require 'spec_helper'
require 'pry'
require 'nokogiri'


describe 'servlet::config::sp' do

    let(:params) {
        {
            :mysql_password => "foo",
            :mysql_endpoint => "localhost"
        }
    }

    describe "web.xml" do

        it { should contain_file("/usr/share/aerofs-sp/sp/WEB-INF/web.xml") }

        before do
            @file = subject.resource("Servlet::Config::File", "/usr/share/aerofs-sp/sp/WEB-INF/web.xml")
            @doc = Nokogiri::XML(@file[:content]) { |config| config.options = Nokogiri::XML::ParseOptions::STRICT }
        end

        describe "SPDatabase" do
            it { @doc.should contain_tag("res-ref-name").with_value("jdbc/SPDatabase") }
            it { @doc.should contain_context_param("sp_database_resource_reference").with_value("jdbc/SPDatabase") }
        end

        # UNCOMMENT THIS TEST TO PRINT OUT THE GENERATED CONFIG FILE
        # it { puts @file[:content] }
    end

    describe "context.xml" do

        it { should contain_file("/etc/tomcat6/Catalina/localhost/ROOT.xml") }

        before do
            @file = subject.resource("Servlet::Config::File", "/etc/tomcat6/Catalina/localhost/ROOT.xml")
            @doc = Nokogiri::XML(@file[:content]) { |config| config.options = Nokogiri::XML::ParseOptions::STRICT }
        end

        it { @doc.should contain_tag("Resource").with_attribute("name").with_value("jdbc/SPDatabase") }
        it { @doc.should contain_tag("Resource").with_attribute("username").with_value("aerofs_spsv") }
        it { @doc.should contain_tag("Resource").with_attribute("password").with_value("foo") }
        it { @doc.should contain_tag("Resource").with_attribute("url").with_value("jdbc:mysql://localhost/aerofs_sp") }

        # UNCOMMENT THIS TEST TO PRINT OUT THE GENERATED CONFIG FILE
        # it { puts @file[:content] }
    end
end
