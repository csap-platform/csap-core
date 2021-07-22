define( [ "services/os-app-graph", "browser/utils" ], function ( osAndAppGraphs, utils ) {

    console.log( "Module  loaded new" ) ;
    let $osTable = $( "#serviceStats tbody" ) ;
    let _hostNameArray = null ;

    let _showMissingDataMessage = true ;

    let _sevenDayOffset = 1 ;

    let _osKeys = { "topCpu": "Cpu", "threadCount": "Threads", "rssMemory": "Memory",
        "fileCount": "Open Files", "socketCount": "Sockets",
        "diskUtil": "Disk Used", "diskWriteKb": "Disk Writes(kb)" } ;

    let _needsInit = true ;

    let serviceName, serviceNameOnly, hostName, servicePerformanceId, resourceLimits, _graphReleasePackage ;

    let containerIndex = 0 ;

    return {
        // 
        show: function ( service, hostNameArray, theResourceLimits ) {
            
            _hostNameArray = hostNameArray ;
            
            if ( _hostNameArray.length == 0  ) {
                console.log( `No hosts selected`) ;
            }
            
            hostName = _hostNameArray[0] ;
            
            serviceName = service ;
            console.log( `service: ${ service }, hostNameArray: ${ hostNameArray } ` ) ;
            serviceNameOnly = service ;
            servicePerformanceId = service ;
            if ( utils.isSelectedKubernetes() ) {
                servicePerformanceId = utils.selectedKubernetesPod() ;
            }
            serviceShortName = service ;
            resourceLimits = theResourceLimits ;
            _graphReleasePackage = uiSettings.projectParam ;
            osAndAppGraphs.updateHosts( _hostNameArray ) ;
            show() ;
        },
        updateOffset: function ( numDays ) {
            _sevenDayOffset = numDays ;
        }

    }

    function init() {

        if ( _needsInit ) {
            _needsInit = false ;
            $( "#viewAlertsColumn" ).click( function () {
                $( ".limitsColumn" ).toggle() ;
            } )

            $( '#osProcessAnalytics' ).click( function () {
                let urlAction = `${ analyticsUrl }&project=${_graphReleasePackage }&report=${METRICS_OS_PROCESS}/detail`
                        + `&service=${ serviceNameOnly }&appId=${ APP_ID }&` ;

                openWindowSafely( urlAction, "_blank" ) ;
                return false ;
            } ) ;

            $( '#hostAnalytics' ).click( function () {
                let urlAction = `${ analyticsUrl }&project=${_graphReleasePackage }&report=tableHost`
                        + `&appId=${ APP_ID }&` ;

                openWindowSafely( urlAction, "_blank" ) ;
                return false ;
            } ) ;

        }

    }

    function show() {

        init() ;

        let paramObject = {
            serviceName: serviceName,
            hostName: _hostNameArray
        } ;

        $osTable.empty() ;
        let $appRow = jQuery( '<tr/>', { } ) ;
        $appRow.append( jQuery( '<td/>', { "colspan": "99", "html": '<div class="loadingPanel">Retrieving current, 24 hour and 7 day</div>' } ) )
        $osTable.append( $appRow ) ;

        // console.log("getServiceReport: " + report + " days: " + numDays)
        // ;
        $.getJSON(
                "service/resources",
                paramObject )

                .done( function ( responseJson ) {

                    service_resource_response_handler( responseJson ) ;

                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {
                    console.log( "Error: getLatestServiceStats " + hostName + " reason:", errorThrown )
                    // handleConnectionError( "Retrieving
                    // lifeCycleSuccess fpr host
                    // " + hostName , errorThrown ) ;
                } ) ;

    }

    function service_resource_response_handler( responseJsonArray ) {
        console.log( " getLatestServiceStatsSuccess(): ", responseJsonArray ) ;
        //_latestServiceMetrics = responseJson;

        let mergedServiceData = mergeArrayValues( responseJsonArray.data ) ;
        $osTable.empty() ;
//        if ( !mergedServiceData ) {
//            // alertify.alert() ;
//            let $appRow = jQuery( '<tr/>', {} ) ;
//            let message = "Real time data not" ;
//            $appRow.append( jQuery( '<td/>', {"colspan": "99",
//                "html": '<div class="">' + message + '</div>'} ) )
//            $osTable.append( $appRow ) ;
//            return ;
//        }

        // initialize all values to be empty
        //$( "#serviceStats >div span" ).text( "-" );

        for ( let osResourceKey in  _osKeys ) {
            let graphId = getGraphId( osResourceKey ) ;
            let osClass = "stats" + osResourceKey ;
            let $osRow = jQuery( '<tr/>', {
                "data-graph": graphId,
                "data-type": METRICS_OS_PROCESS,
                class: osClass
            } ) ; //.hover( serviceGraph.show, serviceGraph.hide );



            let currentValue = getValue( osResourceKey, mergedServiceData ) ;
            let rawValue = parseFloat( currentValue ) ;


            let limit = parseInt( resourceLimits[ osResourceKey ] ) ;
            if ( osResourceKey == "diskUtil" && isDiskGb() ) {

                limit = parseFloat( limit / 1024 ) ;
            }

            let labelClass = "status-green" ;
            let description = "Click to view graph." ;
            let numHosts = responseJsonArray.data.length ;

            // console.log( "osResourceKey: " + osResourceKey + " limit: " + limit + " currentValue: " + currentValue);
            if ( ( parseInt( currentValue ) / numHosts ) > limit ) {
                statusImage = '<img class="statusIcon"  src="images/16x16/red.png">' ;
                labelClass = "resourceWarning status-red" ;
                description += "\n\nNote: Current value exceeds specified limit: " + limit ;
            }

            let $labelCell = jQuery( '<td/>', {
                title: "Click to view trends",
                class: "showGraphCell",
                "data-raw": rawValue,
                "data-number": numHosts
            } ).appendTo( $osRow ) ;

            $labelCell.click( function () {
                osAndAppGraphs.show(
                        $( this ),
                        serviceName ) ;
            } ) ;

            let $label = jQuery( '<span/>', {
                class: labelClass,
                title: description,
                html: _osKeys[ osResourceKey ],
            } ) ;

            $label.appendTo( $labelCell ) ;

            let $currentLink = jQuery( '<a/>', {
                class: "simple",
                title: "Click to launch host dashboard, opening the selected graph",
                href: "#ViewCurrentOsUsage",
                "data-resource": graphId,
                html: currentValue
            } ).click( function () {

//                let dashUrl = utils.agentUrl( hostName ) + "?service=" + servicePerformanceId + "&graph=" + getGraphIdFromAttribute( $( this ) ) ;
//
//                console.log( "launching: " + dashUrl ) ;
//                utils.launch( dashUrl ) ;

                    let osParams = { defaultService: servicePerformanceId, osGraph: getGraphIdFromAttribute( $( this ) )} ;
                    utils.openAgentWindow( hostName, "/app-browser#agent-tab,service", osParams ) ;
                    
                return false ;
            } ) ;

            let $currentCell = jQuery( '<td/>', { } ) ;
            $currentCell.append( $currentLink ) ;
            $currentCell.appendTo( $osRow ) ;

            jQuery( '<td/>', {
                class: "day1",
                text: "-",
            } ).appendTo( $osRow ) ;

            jQuery( '<td/>', {
                class: "day7",
                text: "-",
            } ).appendTo( $osRow ) ;

//			let $averageCell = jQuery( '<td/>', { class: "average" } ).appendTo( $osRow );
//			jQuery( '<div/>', { class: "day1", text: "-" } ).appendTo( $averageCell );
//			jQuery( '<div/>', { class: "day7", text: "-" } ).appendTo( $averageCell );


            if ( osResourceKey == "diskUtil" && isDiskGb() ) {
                limit = ( limit / 1000 ).toFixed( 1 ) + getUnits( true ) ;
            }

            jQuery( '<td/>', {
                class: "limitsColumn",
                html: limit,
            } ).appendTo( $osRow ) ;


            $osTable.append( $osRow ) ;
        }
        getServiceReport( 1 ) ;
        return ;
    }

    function getValue( osKey, osData ) {



        if ( osData && osData.containers ) {
            osData = osData.containers[containerIndex] ;
        }

        let osValue = "-" ;

        let isParse = false ;
        let divideBy = 1 ;

        let isRealTime = false ;

        if ( osData ) {
            if ( osData.cpuUtil && osData.cpuUtil != "-" ) {
                isParse = true ;
                isRealTime = true ;
            }

            if ( !isRealTime && osData.topCpu != undefined ) {
                isParse = true ;
            }
            if ( osData.numberOfSamples ) {
                divideBy = osData.numberOfSamples ;
            }
        }



        if ( isParse ) {
            osValue = Math.round( osData[ osKey ] / divideBy ) ;

            if ( osKey == "topCpu" ) {
                osValue = ( osData[ osKey ] / divideBy ).toFixed( 1 ) + '<div class="units">%</div>' ;
            }

            if ( osKey == "rssMemory" ) {
                if ( isRealTime ) {
                    // real time always returns raw bytes
                    // osValue = Math.round( osValue / 1024 );
                    osValue = osValue ;
                }
                if ( isMemoryMb() ) {
                    osValue = ( osValue ).toFixed( 0 ) + getUnits() ;
                } else if ( isMemoryGb() ) {
                    osValue = ( osValue / 1024 ).toFixed( 1 ) + getUnits( true ) ;
                }
            }

            if ( osKey == "diskUtil" ) {

                if ( isDiskGb() ) {
                    osValue = ( osValue / 1024 ).toFixed( 1 ) + getUnits( true ) ;
                } else {
                    osValue = osValue + getUnits() ;
                }
            }

        }

//		console.log("getValue() : " + osKey + " isParse: " + isParse + " osValue:" + osValue)

        //console.log(JSON.stringify( osData, null, "\t" ) + " getValue() : osKey: " + osKey + " returning: " + osValue) ;

        return osValue ;

    }

    function getUnits( isGb ) {
        let unit = "Mb" ;
        if ( isGb )
            unit = "Gb" ;
        return '<div class="units">' + unit + '</div>' ;
    }



    /** @memberOf ServiceAdmin */
    function isMemoryMb() {
        return  ( resourceLimits[ "rssMemory" ].search( /m/i ) != -1 ) ;
    }


    /** @memberOf ServiceAdmin */
    function isMemoryGb() {
        return  ( resourceLimits[ "rssMemory" ].search( /g/i ) != -1 ) ;
    }


    /** @memberOf ServiceAdmin */
    function isDiskGb() {
        return  ( resourceLimits[ "diskUtil" ] > 1000 ) ;
    }

    function getServiceReport( numDays, reportStartOffset ) {

        if ( reportStartOffset == undefined )
            reportStartOffset = 0 ;



        let targetHost = hostName ;
        if ( window.isDesktop ) {
            targetHost = uiSettings.testHost  // agent testing
            // targetProject = "All Packages" ;   
            console.log( "Hook for desktop: " + _graphReleasePackage + " targetHost: " + targetHost + " For agent: uncomment previous line" ) ;
        }


        let serviceId = serviceNameOnly ;


        let paramObject = {
            appId: APP_ID,
            report: METRICS_OS_PROCESS,
            project: uiSettings.projectParam,
            life: uiSettings.life,
            serviceName: serviceId,
            numDays: numDays,
            dateOffSet: reportStartOffset
        } ;

        if ( _hostNameArray.length == 1 && ( !utils.isSelectedKubernetes() ) ) {
            $.extend( paramObject, {
                host: targetHost
            } ) ;
        }

        console.log( "getServiceReport(): ", reportUrl, paramObject ) ;
        // console.log("getServiceReport: " + report + " days: " + numDays)
        // ;
        $.getJSON(
                reportUrl,
                paramObject )

                .done( function ( responseJson ) {
                    showReportAverage( responseJson, reportStartOffset ) ;
                    //getServiceReport( 7, _compareOffset );
                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {
                    //reportSuccess( null );
                    console.log( "Error: Retrieving lifeCycleSuccess for:" + reportUrl, errorThrown )
                    // handleConnectionError( "Retrieving
                    // lifeCycleSuccess fpr host
                    // " + hostName , errorThrown ) ;
                } ) ;

    }

    function getMetricLabel( performanceMetric ) {
        let osClass = " .stats" + performanceMetric ;
        return $( "td:nth-child(1)", osClass ) ;
    }
    function showReportAverage( responseJson, reportStartOffset ) {

        console.log( `showReportAverage() - reportStartOffset: ${reportStartOffset}` ) ;
        //console.log( " showReportAverage(): " + JSON.stringify( responseJson, null, "\t" ) );

        let mergedHostReports = mergeArrayValues( responseJson.data ) ;
        if ( !mergedHostReports ) {
            if ( _showMissingDataMessage ) {
                alertify.notify( "Historical data not found - verify application - settings - data collection." ) ;
                _showMissingDataMessage = false ;
            }
            console.log( "showReportAverage() - Did not find data for service", responseJson )
            return ;
        }

        let reportColumn = "td:nth-child(" + ( reportStartOffset + 3 ) + ")" ;

        for ( let osResourceName in  _osKeys ) {

            let osClass = ".stats" + osResourceName ;

            let reportAverage = getValue( osResourceName, mergedHostReports ) ;
            let rawReport = 0 ;

            if ( mergedHostReports[osResourceName] && mergedHostReports.numberOfSamples ) {
                rawReport = Math.round( mergedHostReports[osResourceName] / mergedHostReports.numberOfSamples ) ;
            }

            let $labelColumn = getMetricLabel( osResourceName ) ;
            // multiple hosts
            let rawCurrent = ( $labelColumn.data( "raw" ) / $labelColumn.data( "number" ) ) ;
            if ( ( osResourceName == "rssMemory" || osResourceName == "diskUtil" )
                    && reportAverage.contains( "Gb" ) ) {
                // special case for GB
                rawCurrent = ( rawCurrent * 1024 ).toFixed( 1 ) ;
            }

            let attributePercent = Math.round(
                    Math.abs( ( rawCurrent - rawReport ) / rawReport ) * 100 ) ;

            // lots of corner cases...
            if ( rawCurrent <= 1 && parseFloat( reportAverage ) <= 1 ) {
                attributePercent = 0 ;
            }

//			console.log( "showReportAverage() current: " + osResourceName + " reportAverage: " + reportAverage
//					+ " rawCurrent: " + rawCurrent + " rawReport: " + rawReport
//					+ " attributePercent: " + attributePercent + " reportStartOffset: " + reportStartOffset
//					+ " column raw: " + $labelColumn.data( "raw" ) );

            let filterThreshold = $( "#filterThreshold" ).val() ;

            let warningPresent = $( ".resourceWarning", $labelColumn ).length > 0 ;
            //if ()
            if ( !warningPresent && attributePercent > filterThreshold && reportStartOffset == 0 ) {
                let msg = "Last collected value differs from 24 hour average by " + attributePercent + "%" ;
                $( "span", $labelColumn )
                        .removeClass()
                        .addClass( "resourceWarning status-yellow" )
                        .attr( "title", msg ) ;

//                $( "img", $labelColumn ).attr( "src", "images/16x16/yellow.png" ) ;

            }

            //console.log( "showReportAverage() osClass: " + osClass + " reportColumn:" + reportColumn );
            let $historicalLink = jQuery( '<a/>', {
                class: "simple",
                title: "Click to launch analytics dashboard, opening the selected graph",
                href: "#ViewHistorical",
                "data-resource": osResourceName,
                html: reportAverage
            } ).click( function () {

                let hostsParam = utils.buildHostsParameter() ;

                let urlAction = analyticsUrl + "&graph=" + getGraphIdFromAttribute( $( this ) ) + "&project=" + _graphReleasePackage
                        + "&report=graphOsProcess&host=" + hostsParam
                        + "&service=" + servicePerformanceId + "&appId=" + APP_ID + "&" ;
                
                console.log(`launching: ${ urlAction }`);

                utils.launch( urlAction, "_blank" ) ;
                return ;
            } ) ;
            $( reportColumn, osClass ).empty() ;
            $( reportColumn, osClass ).append( $historicalLink ) ;

        }

        if ( reportStartOffset == 0 ) {
            _showMissingDataMessage = false ;
            getServiceReport( 7, _sevenDayOffset ) ;
        }
    }

    function getGraphId( name ) {
        let graphId = name ;

//		if ( graphId.contains( "rssMemory" ) || graphId.contains( "diskUtil" ) )
//			graphId += "InMB";
//
//		if ( graphId.contains( "topCpu" ) )
//			graphId = "Cpu_15s";

        return graphId ;
    }
    // historical hack for some graphs
    function getGraphIdFromAttribute( $link ) {
        let graphId = $link.data( "resource" ) ;

        return graphId ;
    }

    // ForAllServicePackages
    function mergeArrayValues( inputArray ) {

        //return inputArray[0] ;

        let mergedArray = inputArray[0] ;

        for ( let i = 1 ; i < inputArray.length ; i++ ) {
            for ( let attribute in mergedArray ) {
                let mergeValue = mergedArray[ attribute ] ;
                let iterValue = inputArray[i][ attribute ] ;
                if ( _osKeys[ attribute ] ) {
                    iterValue = parseInt( iterValue ) ;
                    mergeValue = parseInt( mergeValue ) ;
                }

                if ( $.isNumeric( mergeValue ) && $.isNumeric( iterValue ) ) {
                    mergedArray[ attribute ] = mergeValue + iterValue ;
                    //console.log( attribute + " numeric mergeValue: " + mergeValue + " iterValue: " + iterValue)
                } else {

                    //console.log( attribute + " nonNUMERIC mergeValue: " + mergeValue + " iterValue: " + iterValue)
                }

            }
        }


        return mergedArray ;
    }

} ) ;