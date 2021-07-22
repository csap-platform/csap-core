# du , find disk folder sizes and sort

# Notes:
# 1. Set directory as needed
# 2. If running against ROOT owned filesystem, switch user to root

requestedFolder="_file_"
cd $requestedFolder

print_with_head "du - disk usage report: '$requestedFolder'"
du --summarize --human-readable --one-file-system * | sort --reverse --human-numeric-sort


# alternate: du --summarize --block-size=1M --one-file-system

