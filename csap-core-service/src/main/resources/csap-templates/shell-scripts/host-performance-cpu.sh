# testCpu, run a script that stresses CPU - with timed results


scriptName=`basename $0`
print_with_date "Optional parameter: <numIterations>"
print_line  "Expected throughput can vary, but typically 1 million will take about 5 seconds"
print_line  "If results are greater then 12 seconds, validate VM compute limits"

alias time="/usr/bin/time --format=\"\nReal: %e \t User: %U \t System: %S \t Pcpu: %P\n\""

million=1000000
numLoops=$(( 1 * million )) ;
if [ $# == 1 ]   ; then
	numLoops="$1"
fi;

timeOutput=$( (time $(i=$numLoops; while (( i > 0 )); do (( i=i-1 )); done) ) 2>&1)
	
print_line "$timeOutput"
numberOfSeconds=$(echo $timeOutput | awk '{print $2}')

message="Time to run: $numberOfSeconds seconds"

if (( ${numberOfSeconds%.*} < 6 )) ; then
	message+=" GOOD"
	
elif (( ${numberOfSeconds%.*} < 7 )) ; then
	message+=" BAD"
else
	message+=" REALLY BAD"
fi

print_with_head $message