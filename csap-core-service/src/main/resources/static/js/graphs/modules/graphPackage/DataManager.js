
// flot timestamps require large integer support to calculate timestamp offsets for custom timezone settings.

define( ["mathjs"], function ( mathjs ) {

    console.log( "Module loaded: graphPackage/DataManager" ) ;

    // Offset used in timezone calculation
    var MS_IN_MINUTE = mathjs.bignumber( 60000 ) ;

    function DataManager ( hostArray ) {
        
        var selectedHosts = hostArray ;
        
        var hostsWithMissingData = 0 ;


        var _stackedGraphCache = new Object() ;
        this.clearStackedGraphs = function () {
            _stackedGraphCache = new Object() ;
        } ;
        
        this.removeHost = function ( hostName ) {
            console.log(`warning: host ${ hostName } does not contain valid data`) ;
            selectedHosts.splice($.inArray(hostName, selectedHosts), 1);
            //$(".csap-dev04Container").remove() ;
        } ;

        this.getStackedGraph = function ( graphName ) {
            return _stackedGraphCache[ graphName ] ;
        } ;

        this.getStackedGraphCount = function ( graphName ) {
            return _stackedGraphCache[ graphName ].length ;
        } ;

        this.initStackedGraph = function ( graphName ) {
            _stackedGraphCache[ graphName ] = new Array() ;
        } ;
        this.pushStackedGraph = function ( graphName, graphData ) {
            _stackedGraphCache[ graphName ].push( graphData ) ;
            //console.log( `pushed: ${ graphName }`, graphData ) ;
        } ;

        this.getHosts = function () {
            return selectedHosts ;
        } ;
        this.getHostCount = function () {
            return selectedHosts.length  ;
        } ;

        // graphsArray reserved for garbageCollection
        var _graphsArray = new Array() ;

        this.getHostGraph = function ( host ) {
            return _graphsArray[ host ] ;
        } ;

        this.updateHostGraphs = function ( host, flotInstances ) {
            _graphsArray[ host ] = flotInstances ;
        } ;

        this.getAllHostGraphs = function () {
            return _graphsArray ;
        } ;

        // data is cached to support appending latest, redraw with options modified, etc
        var _hostToDataMap = new Array() ;

        this.clearHostData = function ( hostName ) {
            oldHostData = _hostToDataMap[ hostName ] ;
            _hostToDataMap[ hostName ] = undefined ;
            return oldHostData ;
        }

        this.addHostData = function ( hostGraphData ) {
            var host = hostGraphData.attributes.hostName ;
            // alert("_metricsJsonCache[host]: " + _metricsJsonCache[host] ) ;
            if ( _hostToDataMap[host] == undefined ) {
                _hostToDataMap[host] = hostGraphData ;
            } else {
                // flot chokes on out of order data. Old data is appended to newer
                // data

                for ( var key in  hostGraphData.data ) {
                    _hostToDataMap[host].data[key] = hostGraphData.data[key]
                            .concat( _hostToDataMap[host].data[key] ) ;
                }

            }

            return _hostToDataMap[host] ;
        }

        var hackLengthForTesting = -1 ;
        var _dataAutoSampled = false ;

        this.isDataAutoSampled = function () {
            return _dataAutoSampled ;
        } ;

        /**
         * javascript has no bigint support. Hooks exclusively to calculate offset
         * times for graphs to be in local time. JSON uses timestamp as STRINGs,
         * which can be parsed here.
         * 
         * Item 2 - offset should be fixed to avoid graph shifting Item 3 - FLOT has
         * very unique requirements.
         * 
         * @param timeArray
         * @returns {Array}
         */

        this.getLocalTime = getLocalTime ;
        function getLocalTime ( timeArray, offsetString ) {

            // alert (timeArray.length) ;
            // alert ( " Orig:" + new Date(parseInt(timeArray[0]))) ;

            // var timerClone = timeArray.slice(0);
            var timerClone = timeArray ;
            var localArray = new Array() ;
            for ( var i = 0 ; i < timerClone.length ; i++ ) {
                // localArray.push( timeArray[i]-(dt.getTimezoneOffset() * 60000) )
                // ;

                // if ( i > 0 && timerClone[i] >= timerClone[i-1] ) {
                // alert( timerClone[i] + " >= " + timerClone[i-1]) ;
                // }

                if ( hackLengthForTesting != -1 && i > hackLengthForTesting )
                    continue ;

                // using jsbn
//				var origTime = new BigInteger( timerClone[i] );
//
//				var y = new BigInteger( "60000" );
//				var z = new BigInteger( offsetString + "" ); // Big Ints get messed
//				// with - must be a
//				// string
//				var a = y.multiply( z );
//				var result = origTime.subtract( a );


                // using http://mathjs.org/examples/bignumbers.js.html
                var origTime = mathjs.bignumber( timerClone[i] ) ;

                //console.log( "offsetString: " + offsetString ) ;
                var z = mathjs.bignumber( offsetString ) ;  // Big Ints get messed
                // with - must be a string
                var offsetAmount = mathjs.chain( MS_IN_MINUTE ).multiply( z ).done() ;

                var result = mathjs.chain( origTime ).subtract( offsetAmount ).done() ;

                localArray.push( result.toString() ) ;
                //			localArray.push( timerClone[i] );
            }
            // alert (new Date(parseInt(localArray[0])) + " Orig:" + new
            // Date(parseInt(timeArray[0]))) ;
            return localArray ;
        }


        /**
         * Helper function to build x,y points from 2 arrays
         * 
         * Test: alertify.alert( JSON.stringify(buildPoints([1,2,3],["a", "b",
         * "c"]), null,"\t") )
         * 
         * @param xArray
         * @param yArray
         * @returns
         */

        this.buildPoints = buildPoints ;
        function buildPoints ( timeStamps, metricValues, $GRAPH_INSTANCE, graphWidth ) {
            var graphPoints = new Array() ;

            if ( typeof graphWidth == "string" && graphWidth.contains( "%" ) ) {
                var fullWidth = $( window ).outerWidth( true )
                var percent = graphWidth.substring( 0, graphWidth.length - 1 ) ;
                graphWidth = Math.floor( fullWidth * percent / 100 ) ;
            }

            let spacingBetweenSamples = $( ".samplingPoints", $GRAPH_INSTANCE ).val() ;


            let samplingInterval = Math.ceil( timeStamps.length / ( graphWidth / spacingBetweenSamples ) ) ;

            let samplingAlgorithm = $( ".zoomSelect", $GRAPH_INSTANCE ).val()
            let filteringLevels = $( ".meanFilteringSelect", $GRAPH_INSTANCE ).val()
            let isSample = isAutoSample( timeStamps, $GRAPH_INSTANCE ) ;
            if ( isSample && samplingAlgorithm == "Auto") {
                samplingAlgorithm="Mean" ;
            }
            
            console.log(`numPoints available: ${timeStamps.length}  samplingAlgorithm: '${samplingAlgorithm}'`
                + ` samplingInterval: ${samplingInterval} graphWidth:  ${graphWidth}`) ;

            var metricAlgorithmValue = -1 ;
            var numberOfPoints = 0 ;

            var filteredValues = new Array() ;

            var filterCount = 0 ;
            if ( filteringLevels != 0 ) {
                var mean = mathjs.mean( metricValues ) ;
                var maxFilter = filteringLevels * mean ;
                var minFilter = mean / filteringLevels ;
                for ( var i = 0 ; i < metricValues.length ; i++ ) {
                    filteredValues[i] = false ;
                    if ( metricValues[i] > maxFilter || metricValues[i] < minFilter ) {
                        filteredValues[i] = true ;
                        filterCount++ ;
                    }
                }
                console.log( "Pruning: items: ", filterCount, " from total items: ", metricValues.length ) ;
            }

            for ( let i = 0 ; i < timeStamps.length ; i++ ) {


                let metricValue = 0 ;

                // metrics are reversed for forward processing....
                let reverseIndex = timeStamps.length - i ;

                if ( filteredValues.length > 0 && filteredValues[ reverseIndex ] ) {
                    continue ;
                }

                if ( metricValues.length )
                    metricValue = metricValues[ reverseIndex ] ;
                else
                    metricValue = metricValues ;

                if ( isSample ) {

                    switch ( samplingAlgorithm ) {

                        case "Max":
                            if ( metricValue > metricAlgorithmValue )
                                metricAlgorithmValue = metricValue ;
                            break ;

                        case "Min":
                            if ( metricValue < metricAlgorithmValue || metricAlgorithmValue < 0 )
                                metricAlgorithmValue = metricValue ;
                            break ;
                            
                        case "Auto":
                        case "Mean":
                            metricAlgorithmValue += metricValue ;
                            break ;

                    }

                    numberOfPoints++ ;
                    if ( i % samplingInterval != 0 && i != reverseIndex ) {
                        continue ;
                    } else {

                        metricValue = metricAlgorithmValue ;
                        var resetValue = -1 ;
                        if ( samplingAlgorithm == "Mean" ) {

                            metricValue = metricAlgorithmValue / numberOfPoints ;
                            resetValue = 0 ;
                            numberOfPoints = 0 ;
                        }
                        metricAlgorithmValue = resetValue ;
                    }
                }

                var resourcePoint = new Array() ;


                //var timeToSecond=Math.floor(xArray[xArray.length -i]/30000)*30000 ;
                //console.log("orig: " + xArray[i] + " rounded: " + timeToSecond) ;

                // points are reversed for flot stacking to work
                resourcePoint.push( timeStamps[ reverseIndex ] ) ;
                resourcePoint.push( metricValue ) ;

                graphPoints.push( resourcePoint ) ;

            }

            timeStamps = null ;
            metricValues = null ;
            // console.log( "Points: " + JSON.stringify(graphPoints ) );

            return graphPoints ;
        }


        /**
         * too much data slows down UI; data is trimmed if needed
         */
        this.isAutoSample = isAutoSample ;
        function isAutoSample ( timeArray, $GRAPH_INSTANCE ) {


            _dataAutoSampled = false ;
            
            let displaySelection=$( "#numSamples option:selected", $GRAPH_INSTANCE ).text() ;
            
            let sampleLimit=$( ".samplingLimit", $GRAPH_INSTANCE ).val() ;
            if ( timeArray.length > sampleLimit ) {
                
                if ( "Mean Min Max".contains(displaySelection)) {
                    _dataAutoSampled = true ;
                } else if ( ("Auto" == displaySelection) 
                        && timeArray.length > 30*sampleLimit ) {
                    _dataAutoSampled = true ;
                }
            }
            

            console.log(`displaySelection: '${displaySelection}', number of Points: '${timeArray.length}', _dataAutoSampled: ${_dataAutoSampled}`) ;
            return _dataAutoSampled ;
        }

    }

    return DataManager ;


} ) ;
	 