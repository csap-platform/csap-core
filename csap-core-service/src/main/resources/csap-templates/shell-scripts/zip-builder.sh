# zip builder, builds a zip on the filesystem for downloading, saving, etc.
cd _file_

zipName="$HOSTNAME"_demo.zip

print_with_head "Creating: $zipName"

\rm -rf $zipName
find * -name "consoleLogs.txt" -print | zip $zipName -@