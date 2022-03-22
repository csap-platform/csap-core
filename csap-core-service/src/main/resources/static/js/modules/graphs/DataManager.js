

import _dom from "../utils/dom-utils.js";

import _csapCommon from "../utils/csap-common.js" ;


_dom.logHead( "Module loaded" );


let MS_IN_MINUTE = 60000 ;


export default function DataManager( hostArray ) {

    

    let selectedHosts = hostArray;

    let hostsWithMissingData = 0;


    let _stackedGraphCache = new Object();
    this.clearStackedGraphs = function () {
        _stackedGraphCache = new Object();
    };

    this.removeHost = function ( hostName ) {
        console.log( `warning: host ${ hostName } does not contain valid data` );
        selectedHosts.splice( $.inArray( hostName, selectedHosts ), 1 );
        //$(".csap-dev04Container").remove() ;
    };

    this.getStackedGraph = function ( graphName ) {
        return _stackedGraphCache[ graphName ];
    };

    this.getStackedGraphCount = function ( graphName ) {
        return _stackedGraphCache[ graphName ].length;
    };

    this.initStackedGraph = function ( graphName ) {
        _stackedGraphCache[ graphName ] = new Array();
    };
    this.pushStackedGraph = function ( graphName, graphData ) {
        _stackedGraphCache[ graphName ].push( graphData );
        //console.log( `pushed: ${ graphName }`, graphData ) ;
    };

    this.getHosts = function () {
        return selectedHosts;
    };
    this.getHostCount = function () {
        return selectedHosts.length;
    };

    // graphsArray reserved for garbageCollection
    let _graphsArray = new Array();

    this.getHostGraph = function ( host ) {
        return _graphsArray[ host ];
    };

    this.updateHostGraphs = function ( host, flotInstances ) {
        _graphsArray[ host ] = flotInstances;
    };

    this.getAllHostGraphs = function () {
        return _graphsArray;
    };

    // data is cached to support appending latest, redraw with options modified, etc
    let _hostToDataMap = new Array();

    this.clearHostData = function ( hostName ) {
        const oldHostData = _hostToDataMap[ hostName ];
        _hostToDataMap[ hostName ] = undefined;
        return oldHostData;
    }

    this.addHostData = function ( hostGraphData ) {
        let host = hostGraphData.attributes.hostName;
        // alert("_metricsJsonCache[host]: " + _metricsJsonCache[host] ) ;
        if ( _hostToDataMap[ host ] == undefined ) {
            _hostToDataMap[ host ] = hostGraphData;
        } else {
            // flot chokes on out of order data. Old data is appended to newer
            // data

            for ( let key in hostGraphData.data ) {
                _hostToDataMap[ host ].data[ key ] = hostGraphData.data[ key ]
                    .concat( _hostToDataMap[ host ].data[ key ] );
            }

        }

        return _hostToDataMap[ host ];
    }

    let hackLengthForTesting = -1;
    let _dataAutoSampled = false;

    this.isDataAutoSampled = function () {
        return _dataAutoSampled;
    };

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

    this.getLocalTime = getLocalTime;
    function getLocalTime( originalTimestamps, offsetString ) {

        //return originalTimestamps ;
        let timestampsWithOffset = new Array() ;
        for( const gmtTimeString of originalTimestamps ) {

            let origTime = parseInt( gmtTimeString );

            let offsetAmount = MS_IN_MINUTE * parseInt( offsetString ) ;

            let offsetTime = origTime - offsetAmount ;
            //console.log(`origTime: ${origTime} offsetAmount: ${ offsetAmount } offsetTime: ${offsetTime}`)

            timestampsWithOffset.push( offsetTime  ) ;
        }

        //console.log( `originalTimestamps: ${ originalTimestamps[0] }  timestampsWithOffset: ${ timestampsWithOffset[0] }`)

        return timestampsWithOffset ;

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

    this.buildPoints = buildPoints;
    function buildPoints( timeStamps, metricValues, $GRAPH_INSTANCE, graphWidth ) {
        let graphPoints = new Array();

        if ( typeof graphWidth == "string" && graphWidth.contains( "%" ) ) {
            let fullWidth = $( window ).outerWidth( true )
            let percent = graphWidth.substring( 0, graphWidth.length - 1 );
            graphWidth = Math.floor( fullWidth * percent / 100 );
        }

        let spacingBetweenSamples = $( ".samplingPoints", $GRAPH_INSTANCE ).val();


        let samplingInterval = Math.ceil( timeStamps.length / ( graphWidth / spacingBetweenSamples ) );

        let samplingAlgorithm = $( ".zoomSelect", $GRAPH_INSTANCE ).val()
        let filteringLevels = $( ".meanFilteringSelect", $GRAPH_INSTANCE ).val()
        let isSample = isAutoSample( timeStamps, $GRAPH_INSTANCE );
        if ( isSample && samplingAlgorithm == "Auto" ) {
            samplingAlgorithm = "Mean";
        }

        console.log( `numPoints available: ${ timeStamps.length }  samplingAlgorithm: '${ samplingAlgorithm }'`
            + ` samplingInterval: ${ samplingInterval } graphWidth:  ${ graphWidth }` );

        let metricAlgorithmValue = -1;
        let numberOfPoints = 0;

        let filteredValues = new Array();

        let filterCount = 0;
        if ( filteringLevels != 0 ) {
            console.log( `metricValues: `, metricValues) ;
            let mean = _csapCommon.findMedian( metricValues );
            let maxFilter = filteringLevels * mean;
            let minFilter = mean / filteringLevels;
            for ( let i = 0; i < metricValues.length; i++ ) {
                filteredValues[ i ] = false;
                if ( metricValues[ i ] > maxFilter || metricValues[ i ] < minFilter ) {
                    filteredValues[ i ] = true;
                    filterCount++;
                }
            }
            console.log( "Pruning: items: ", filterCount, " from total items: ", metricValues.length );
        }

        for ( let i = 0; i < timeStamps.length; i++ ) {


            let metricValue = 0;

            // metrics are reversed for forward processing....
            let reverseIndex = timeStamps.length - i;

            if ( filteredValues.length > 0 && filteredValues[ reverseIndex ] ) {
                continue;
            }

            if ( metricValues.length )
                metricValue = metricValues[ reverseIndex ];
            else
                metricValue = metricValues;

            if ( isSample ) {

                switch ( samplingAlgorithm ) {

                    case "Max":
                        if ( metricValue > metricAlgorithmValue )
                            metricAlgorithmValue = metricValue;
                        break;

                    case "Min":
                        if ( metricValue < metricAlgorithmValue || metricAlgorithmValue < 0 )
                            metricAlgorithmValue = metricValue;
                        break;

                    case "Auto":
                    case "Mean":
                        metricAlgorithmValue += metricValue;
                        break;

                }

                numberOfPoints++;
                if ( i % samplingInterval != 0 && i != reverseIndex ) {
                    continue;
                } else {

                    metricValue = metricAlgorithmValue;
                    let resetValue = -1;
                    if ( samplingAlgorithm == "Mean" ) {

                        metricValue = metricAlgorithmValue / numberOfPoints;
                        resetValue = 0;
                        numberOfPoints = 0;
                    }
                    metricAlgorithmValue = resetValue;
                }
            }

            let resourcePoint = new Array();


            //let timeToSecond=Math.floor(xArray[xArray.length -i]/30000)*30000 ;
            //console.log("orig: " + xArray[i] + " rounded: " + timeToSecond) ;

            // points are reversed for flot stacking to work
            resourcePoint.push( timeStamps[ reverseIndex ] );
            resourcePoint.push( metricValue );

            graphPoints.push( resourcePoint );

        }

        timeStamps = null;
        metricValues = null;
        // console.log( "Points: " + JSON.stringify(graphPoints ) );

        return graphPoints;
    }


    /**
     * too much data slows down UI; data is trimmed if needed
     */
    this.isAutoSample = isAutoSample;
    function isAutoSample( timeArray, $GRAPH_INSTANCE ) {


        _dataAutoSampled = false;

        let displaySelection = $( "#numSamples option:selected", $GRAPH_INSTANCE ).text();

        let sampleLimit = $( ".samplingLimit", $GRAPH_INSTANCE ).val();
        if ( timeArray.length > sampleLimit ) {

            if ( "Mean Min Max".contains( displaySelection ) ) {
                _dataAutoSampled = true;
            } else if ( ( "Auto" == displaySelection )
                && timeArray.length > 30 * sampleLimit ) {
                _dataAutoSampled = true;
            }
        }


        console.log( `displaySelection: '${ displaySelection }', number of Points: '${ timeArray.length }', _dataAutoSampled: ${ _dataAutoSampled }` );
        return _dataAutoSampled;
    }

}

