#!/usr/bin/env ruby


require 'rubygems'
require 'json'
require 'optparse'
require 'yaml'


options = {}
optparse = OptionParser.new do |opts|
  opts.banner = "Usage: stack_cups.rb [options]"

  opts.on('-r', '--root PLAN ROOT', 'Plan Root Directory') { |v| options[:plan_root] = v }
 
end

begin
  optparse.parse!

  @planRoot = options[:plan_root]

  if(@planRoot.nil?)
    puts optparse
    exit;
  end

end

actions = Array.new
actions.push "move \"#{@planRoot}\\cup3\\cup2\" \"#{@planRoot}\""
actions.push "move \"#{@planRoot}\\cup2\\cup1\" \"#{@planRoot}\""
actions.push "move \"#{@planRoot}\\cup1\\ball.txt\" \"#{@planRoot}\""

actions.each do |action|
  puts "#{action}"
  system "#{action}"
  sleep(1)
end
  




