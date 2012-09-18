require 'rspec-puppet'

# Prepare RSpec for tests
fixture_path = File.expand_path(File.join(__FILE__, '..', 'fixtures'))

RSpec.configure do |c|
  c.module_path = File.join(fixture_path, 'modules')
  c.manifest_dir = File.join(fixture_path, 'manifests')
end

# Custom Rspec Matcher to check context-params in web.xml files
RSpec::Matchers.define :contain_context_param do |expected|
    chain :with_value do |value|
        @value = value
    end
    match do |actual|
        text = actual.xpath("//web-app/context-param/param-name[.='#{expected}']/following-sibling::param-value").text
        text == @value
    end
    description do
        "contain context param \"#{expected}\" with value \"#{@value}\""
    end
end

# Custom Rspec Matcher to check for tags in xml files (uses nokogiri)
RSpec::Matchers.define :contain_tag do |expected|
    # specify the expected value
    chain :with_value do |value|
        @value = value
    end
    # specify what attribute to check. If this is not specified, we default to the node's text
    chain :with_attribute do |attribute|
        @attribute = attribute
    end

    match do |actual|

        is_a_match = false

        if @attribute.nil?
            # if any tags match our value, set is_a_match to true
            actual.xpath("//#{expected}").each do |node|
                is_a_match ||= node.text == @value
            end
        else
            # if any tags with the attribute match our value, set is_a_match to true
            actual.xpath("//#{expected}").each do |node|
                tag_attribute = node.attributes[@attribute]
                if tag_attribute
                    is_a_match ||= @value == node.attributes[@attribute].value
                end
            end
        end
        # if we found anything, return true
        is_a_match
    end
    description do
        if @attribute
            "contain tag \"#{expected}\" with attribute \"#{@attribute}\" with value \"#{@value}\"" if @attribute
        else
            "contain tag \"#{expected}\" with value \"#{@value}\""
        end
    end
end
