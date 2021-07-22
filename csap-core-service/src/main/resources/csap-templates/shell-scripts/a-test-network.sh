# validation: network, Verifies network connectivity to resource such as LDAP, maven, etc



numRuns=1;

exit_if_not_installed timeout ;
exit_if_not_installed nc ;

function testConnection() {
	host="$1" ;
	port="$2" ;
	#$words=`timeout 2 nc -w 1 $host $port 2>&1 | wc -w`
	
	ncat_result=$(ncat -w 2 -zv $host $port 2>&1)
	result="Pass" ;
	#echo "'"$words"'"
	if [[ $ncat_result == *failed*  ]] ; then
		result="Fail" ;
	fi ;
	print_with_head "$host $port $result"
}

for (( run=1; run < (numRuns+1) ; run++ ))
do
	print_with_head run $run of $numRuns
	testConnection localhost 8011
	# testConnection csaptools 80
	# testConnection csaptools 81
	#testConnection your-maven 80
	#testConnection your-ldap 389
	#testConnection tns-prod 5000
	#testConnection your-prod-lb 80
done
exit


# note -z option of nc is not support in rh 7.2 yet.

numRuns=1;

print_with_head number of iterations to test: $numRuns

for (( run=0; run < numRuns ; run++ ))
do  
	print_with_head "==== Run " $run of $numRuns
	nc -w 2 -zv $toolsServer 80
	nc -w 2 -z [[ ${ csapLbUrl } ]] 80
done

