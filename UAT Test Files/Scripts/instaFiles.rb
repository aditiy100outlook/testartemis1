#!/usr/bin/env ruby
#This script will create X number of files of Y size each.  It requires two arguments: the number of files you want to generate, and the desired file size (in kilobytes).  Example: "./instaFiles.rb 12 20000" will create twelve 20 MB files (each one will be a unique binary file filled with random bits).

n=ARGV[0].to_i

for i in 1..n do

make_files = "dd if=/dev/random of=File#{i} bs=1k count=#{ARGV[1]}"

system make_files

end