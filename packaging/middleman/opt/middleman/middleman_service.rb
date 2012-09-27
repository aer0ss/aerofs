require 'rubygems'
require 'daemons'

Daemons.run 	'/opt/middleman/middleman.rb',
		:dir_mode => :normal,
		:dir => "/tmp"
