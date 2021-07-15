#!/bin/bash
 
# Notes:
# 1. Set the filter as needed
# 2. Tail/Grep commands can put significant load on VM, use CSAP vm monitor to observe impact

filter="__searchTarget__" ;
maxMatches="__maxMatches__"
maxDepth="__maxDepth__" ;
linesBefore=__linesBefore__
linesAfter=__linesAfter__
ignoreCase="__ignoreCase__" ;
tailLines="__tailLines__"
reverseOrder="__reverseOrder__"
zipSearch="__zipSearch__" ;
includeFileName=${includeFileName:-} ;


location="__searchLocation__"
searchDir=`dirname "$location"`
searchName=`basename "$location"`

# echo == Switching to $searchDir to shorten file output
cd $searchDir

delim="__delim__";
groupCommandSupported=`grep --group-separator=yes 2>&1 | grep unrecognized | wc -l`
if [ $groupCommandSupported == 1 ] ; then 
	echo == disabling group-separator as not supported by OS , check OS version
	delim=""
fi;



# uncomment to debug

numberOfFileMatches=$(find . -name "$searchName" -type f -maxdepth $maxDepth | wc -l) ;
if (( $numberOfFileMatches > 1 )); then
	includeFileName="--with-filename" ;
fi ;

#echo -e  "\n\n searching '$location' for '$filter', files found: '$numberOfFileMatches', maxMatches: '$maxMatches', ignoreCase: $ignoreCase reverseOrder: $reverseOrder\n\n" ;


if [ "$tailLines" != "" ] ; then
	tail -$tailLines $location | grep $delim -A $linesAfter -B $linesBefore $maxMatches $ignoreCase $includeFileName "$filter"
	exit ;
fi ;


if [ "$reverseOrder" == "true" ] ; then

	 if [ $zipSearch != "true" ] ; then 

		numMatches=$(ls -l $location 2>&1 | wc -l)
	
		for fileName in $( find $searchDir -name "$searchName"  -maxdepth $maxDepth -type f ) ; do 
		
			if [ $numMatches != 1 ] ; then 
				echo ===== Searching File :  $fileName ;echo
			fi ;
			
			tac $fileName | grep $delim -A $linesAfter -B $linesBefore $maxMatches $ignoreCase $includeFileName "$filter"
	      	
		done

	  else
			echo "Command aborted: Reverse searches can only be done on text files"
	  fi

	exit ;
fi 

# default search

if [ $zipSearch != "true" ] ; then 

	 # grep $delim -A $linesAfter -B $linesBefore $maxMatches $ignoreCase --fixed-strings "$filter" $searchName
	filesWithMatches=$(find . -name "$searchName"  \
		-maxdepth $maxDepth -type f \
		-exec grep $ignoreCase --fixed-strings  "$filter" {} --files-with-matches \;) ;
		
	numMatches=$(echo $filesWithMatches  | wc -w)
	
	if (( $numMatches == 0 )) ; then
		echo "no matches found"
	else
		grep $delim -A $linesAfter -B $linesBefore $maxMatches $ignoreCase $includeFileName --fixed-strings  "$filter" $filesWithMatches 
	fi

else
	 zgrep $delim -A $linesAfter -B $linesBefore $maxMatches $ignoreCase "$filter" $searchName
fi


