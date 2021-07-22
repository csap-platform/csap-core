# jar finder, searches folder recursively for unique jar names

cd _file_

count=`find * -name "*.jar" -exec basename {} \;|  sort | uniq | wc -l`

print_with_head "Unique jar files: $count, Listing: "

find * -name "*.jar" -exec basename {} \;|  sort | uniq