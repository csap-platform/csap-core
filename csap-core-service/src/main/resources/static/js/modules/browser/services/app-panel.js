// define( [ "browser/utils", "services/os-app-graph" ], function ( utils, osAndAppGraphs ) {

import _dom from "../../utils/dom-utils.js";
import _net from "../../utils/net-utils.js";


import utils from "../utils.js"
import osAndAppGraphs from "./os-app-graph.js"


export const appPanel = app_panel();

// export default appPanel


function app_panel() {

    _dom.logHead( "Module loaded" );

    console.log( "Module  loaded" );

    let _reportType;
    let _hostNameArray = null;

    let _sevenDayOffset = 1;


    const $analyticsTab = $( "#services-tab-analytics" );
    const $appTable = $( "#appStats", $analyticsTab );

    const $noMetricsBody = $( "#no-metrics-body", $appTable );
    const $metricsBody = $( "#metrics-body", $appTable );

    const jmxRates = {
        httpRequestCount: { reportRate: "perHour" },
        sessionsCount: { reportRate: "perDay" },
    }

    const reportRateLookup = {
        perSecond: 1 / 30,
        per30Second: 1,
        perMinute: 2,
        perHour: 120,
        perDay: 2880
    }

    let _needsInit = true;

    let serviceName, serviceNameOnly, serviceShortName, hostName, customMetrics, jmxLabels;

    let _graphReleasePackage, life;


    let containerIndex = 0;

    return {
        // peter
        show: function ( service, hostNameArray, performanceDefinition, javaLabels ) {
            _hostNameArray = hostNameArray;
            serviceName = service;
            serviceNameOnly = service;
            serviceShortName = service;

            if ( utils.isSelectedKubernetes() ) {
                serviceShortName = utils.selectedKubernetesPod();
            }


            customMetrics = performanceDefinition;
            jmxLabels = javaLabels;
            hostName = _hostNameArray[ 0 ];

            osAndAppGraphs.updateHosts( _hostNameArray );
            _graphReleasePackage = uiSettings.projectParam;
            life = uiSettings.life;
            return show();
        },
        updateOffset: function ( numDays ) {
            console.log( "Updated _sevenDayOffset: " + numDays );
            _sevenDayOffset = numDays;
        }

    }

    function init() {

        if ( _needsInit ) {
            _needsInit = false;
            $( 'input[name=metricChoice]' ).click( function () {
                show();
            } );



            $( '#jmxAnalyticsLaunch' ).click( function () {

                let urlAction = analyticsUrl + "&project=" + _graphReleasePackage + "&report=java/detail"
                    + "&service=" + serviceNameOnly + "&appId=" + APP_ID + "&";

                openWindowSafely( urlAction, "_blank" );
                return false;
            } );

            $( '#applicationLaunch' ).click( function () {

                let urlAction = analyticsUrl + "&project=" + _graphReleasePackage + "&report=application/detail"
                    + "&service=" + serviceNameOnly + "&appId=" + APP_ID + "&";

                openWindowSafely( urlAction, "_blank" );
                return false;
            } );

        }

    }

    function show() {


        init();
        _reportType = $( 'input[name=metricChoice]:checked' ).val();

        let serviceId = serviceShortName;

        let paramObject = {
            hostName: _hostNameArray,
            serviceName: serviceId,
            type: _reportType,
            number: $( "#numAppSamples" ).val(),
            interval: 30
        };

        $metricsBody.empty();
        if ( _reportType === "app" &&
            ( customMetrics === null || $.isEmptyObject( customMetrics ) ) ) {
            $noMetricsBody.show();
            $( ".loadingPanel", $noMetricsBody ).hide();
            $( ".info", $noMetricsBody ).show();
            return;
        }

        $noMetricsBody.hide();

        console.log( `show() Latest app report for Service ${ serviceId },  _reportType: ${ _reportType }` );

        let $appRow = jQuery( '<tr/>', {} );
        $appRow.append( jQuery( '<td/>', { "colspan": "99", "html": '<div class="loadingPanel">Retrieving current, 24 hour and 7 day</div>' } ) )
        $metricsBody.append( $appRow );

        
        let viewLoadedPromise = _net.httpGet( "service/query/getLatestAppStats", paramObject);

        viewLoadedPromise
            .then( process_application_report )
            .catch( ( e ) => {
                console.warn( e );
            } );;


        return viewLoadedPromise;


    }

    function calculateAverage( inputArray ) {
        let total = 0;
        for ( let sampleIndex = 0; sampleIndex < inputArray.length; sampleIndex++ ) {
            total += inputArray[ sampleIndex ];
        }
        return total / inputArray.length;
    }

    function process_application_report( allHostsData ) {

        console.log( " process_application_report(): ", allHostsData );


        let serviceHostsArray = new Array();


        for ( let i = 0; i < _hostNameArray.length; i++ ) {
            let hostRow = allHostsData[ _hostNameArray[ i ] ];
            if ( hostRow != undefined && hostRow.data != undefined ) {
                let hostNumericObject = new Object();
                // strip out from array.
                for ( let metricName in hostRow.data ) {

                    //					if ( hostRow.data[ metricName].length && hostRow.data[ metricName].length == 1 ) {
                    //						hostNumericObject[ metricName ] = (hostRow.data[ metricName])[0];
                    //					}
                    let numSamples = hostRow.data[ metricName ].length;
                    if ( numSamples ) {
                        hostNumericObject[ metricName ] = calculateAverage( hostRow.data[ metricName ] );
                    }

                }
                serviceHostsArray.push( hostNumericObject );
            }

        }

        // agent mode does not have host prefix
        if ( serviceHostsArray.length == 0 ) {
            // direct connection?
            let singleHostData = allHostsData.data;
            let hostNumericObject = new Object();
            for ( let metricName in singleHostData ) {
                //serviceHostsArray.push( allHostsData.data );
                hostNumericObject[ metricName ] = calculateAverage( singleHostData[ metricName ] );
            }
            serviceHostsArray.push( hostNumericObject );
        }

        //


        //console.log( " getLatestAppStatsSuccess() input: " + JSON.stringify( serviceHostsArray, null, "\t" ) );
        let mergedServiceData = mergeArrayValues( serviceHostsArray );
        if ( Object.keys( mergedServiceData ).length > 0 ) {
            // latest app sample size is always host count
            mergedServiceData[ "numberOfSamples" ] = serviceHostsArray.length;
        }
        // console.log( " getLatestAppStatsSuccess(): merged: " + JSON.stringify( mergedServiceData, null, "\t" ) );
        //_latestServiceMetrics = responseJson;
        $( "#appStats .loadingPanel" ).hide();

        // initialize all values to be empty
        //$( "#serviceStats >div span" ).text( "-" );
        $metricsBody.empty();
        // return

        build_application_table( serviceHostsArray.length, mergedServiceData );

        getServiceReport( 1 );
        return;
    }

    function build_application_table( numHosts, mergedServiceData ) {

        for ( let graphAndServiceName in mergedServiceData ) {

            let graphName = graphAndServiceName.split( "_" )[ 0 ];
            if ( graphName == "timeStamp" || graphName == "totalCpu" )
                continue;
            // console.log("graphName: " + graphName + " graphAndServiceName:" + graphAndServiceName) ;
            let osClass = "stats" + graphName;

            let $appRow = jQuery( '<tr/>', {
                class: osClass,
                "data-key": graphName,
                "data-graph": graphName,
                "data-type": _reportType
            } );
            // .hover( serviceGraph.show, serviceGraph.hide );


            $metricsBody.append( $appRow );


            let label = getTitle( graphName );
            let currentValue = getValue( graphAndServiceName, mergedServiceData, label );

            let limit =
                getSetting( graphName, "max", null );
            let labelClass = "status-green";
            let description = "";

            let divisor = 1;
            if ( numHosts > 1 ) {
                divisor = numHosts;
            }
            if ( limit != null && parseInt( currentValue / divisor ) > limit ) {
                labelClass = "resourceWarning status-red";
                description = "Current value exceeds specified limit: " + limit;
            }

            let $labelCell = jQuery( '<td/>', {
                title: "Click to view trends",
                class: "showGraphCell",
                "data-raw": mergedServiceData[ graphAndServiceName ],
                "data-number": divisor
            } ).appendTo( $appRow );

            $labelCell.click( function () {
                osAndAppGraphs.show(
                    $( this ),
                    serviceName );
            } );

            let $label = jQuery( '<span/>', {
                class: labelClass,
                title: description,
                html: label,
            } ).appendTo( $labelCell );

            let $currentLink = jQuery( '<span/>', { text: '-' } );
            if ( numHosts > 0 ) {
                $currentLink = jQuery( '<a/>', {
                    class: "simple",
                    href: "#ViewCurrentAppResource",
                    "data-resource": graphName, //graphName graphAndServiceName
                    html: currentValue
                } ).click( function () {

                    if ( _reportType.contains( "app" ) ) {

                        let appParams = { defaultService: serviceName, appGraph: getGraphId( $( this ) ) };
                        utils.openAgentWindow( hostName, `/app-browser#agent-tab,${ METRICS_APPLICATION }`, appParams );
                        //                        dashboard = "/os/application" ;
                        //                        let appUrl = utils.agentUrl( hostName, dashboard
                        //                                + "?hosts=" + hostName
                        //                                + "&service=" + serviceName
                        //                                + "&graph=" + getGraphId( $( this ) ) ) ;
                        //
                        //                        utils.launch( appUrl ) ;
                    } else {
                        let javaParams = { defaultService: serviceName, javaGraph: getGraphId( $( this ) ) };
                        utils.openAgentWindow( hostName, `/app-browser#agent-tab,${ METRICS_JAVA }`, javaParams );
                    }
                    return false;
                } );
            }

            let $currentCell = jQuery( '<td/>', {} );
            $currentCell.append( $currentLink );
            $currentCell.appendTo( $appRow );


            jQuery( '<td/>', {
                class: "day1",
                text: "-",
            } ).appendTo( $appRow );

            jQuery( '<td/>', {
                class: "day7",
                text: "-",
            } ).appendTo( $appRow );

            //			let $averageCell = jQuery( '<td/>', { class: "average" } ).appendTo( $osRow );
            //			jQuery( '<div/>', { class: "day1", text: "-" } ).appendTo( $averageCell );
            //			jQuery( '<div/>', { class: "day7", text: "-" } ).appendTo( $averageCell );


            if ( graphAndServiceName == "diskUtil" && isDiskGb() ) {
                limit = ( limit / 1000 ).toFixed( 1 ) + getUnits( true );
            }

            jQuery( '<td/>', {
                class: "limitsColumn",
                html: limit,
            } ).appendTo( $appRow );


        }
    }

    function getSetting( meterName, element, defaultValue ) {
        if ( customMetrics != null ) {
            let attributeConfig = customMetrics[ meterName ];
            if ( attributeConfig && attributeConfig[ element ] )
                return attributeConfig[ element ];
        }
        return defaultValue;
    }

    function getTitle( meterName ) {
        let graphTitle =
            getSetting( meterName, "title", splitUpperCase( meterName ) );

        // jmx Titles hack - need to get from collector, hardcoding for
        // now.
        if ( jmxLabels[ meterName ] != undefined ) {
            graphTitle = jmxLabels[ meterName ];

            if ( jmxRates[ meterName ] != undefined ) {
                //attributeConfig = jmxRates[ meterName ];
            }
        }

        return graphTitle;
    }

    function getRateAdjust( performanceItemName ) {

        let attributeConfig = null;
        let rateAdjust = 1;
        let rateCustom = getSetting( performanceItemName, "reportRate", null );

        if ( $( '#rateSelect' ).val() != "default" ) {
            rateAdjust = reportRateLookup[ $( '#rateSelect' ).val() ];
        } else if ( rateCustom != null ) {
            rateAdjust = reportRateLookup[ rateCustom ];
            // add custom label...
            let $labelColumn = getMetricLabel( performanceItemName );
            $( "span.customRate", $labelColumn ).remove();
            let $label = jQuery( '<span/>', {
                class: "customRate",
                text: rateCustom,
            } ).appendTo( $labelColumn );
        }

        return rateAdjust;
    }

    function getValue( performanceItemName, appData, label ) {

        if ( !label )
            label = getTitle( performanceItemName );

        let divideBy = 1;

        let appValue = "-";

        if ( appData && ( appData[ performanceItemName ] != "undefined" ) ) {

            if ( appData.numberOfSamples
                && ( performanceItemName != "numberOfSamples" ) ) {
                divideBy = appData.numberOfSamples;
            }

            divideBy = divideBy / getRateAdjust( performanceItemName );

            appValue = Math.round( appData[ performanceItemName ] / divideBy );

            if ( Math.abs( appValue ) > 2000 ) {
                if ( label.toLowerCase().contains( "(mb)" ) ) {
                    appValue = ( appValue / 1024 ).toFixed( 1 );
                    appValue += getUnits( "Gb" )
                } else if ( label.toLowerCase().contains( "(ms)" ) ) {

                    appValue = ( appValue / 1000 ).toFixed( 2 );
                    appValue += getUnits( "s" )
                } else {
                    if ( Math.abs( appValue ) > 1000000 ) {
                        appValue = ( appValue / 1000000 ).toFixed( 2 );
                        appValue += getUnits( "M" )
                    } else {
                        appValue = ( appValue / 1000 ).toFixed( 1 );
                        appValue += getUnits( "K" )
                    }
                }
            }
        }

        return appValue;

    }

    function getUnits( unit ) {
        //		let unit = "Mb";
        //		if ( isGb )
        //			unit = "Gb";
        return '<div class="units">' + unit + '</div>';
    }



    /** @memberOf ServiceAdmin */
    function isMemoryMb() {
        return ( resourceLimits[ "rssMemory" ].search( /m/i ) != -1 );
    }


    /** @memberOf ServiceAdmin */
    function isMemoryGb() {
        return ( resourceLimits[ "rssMemory" ].search( /g/i ) != -1 );
    }


    /** @memberOf ServiceAdmin */
    function isDiskGb() {
        return ( resourceLimits[ "diskUtil" ] > 1000 );
    }

    function getServiceReport( numDays, reportStartOffset ) {

        if ( reportStartOffset == undefined )
            reportStartOffset = 0;


        let targetHost = hostName;
        if ( window.isDesktop ) {
            // hook for desktop testing
            targetHost = uiSettings.testHost  // agent testing
            // targetProject = "All Packages" ;   
            console.log( "Hook for desktop: " + _graphReleasePackage + " targetHost: " + targetHost + " For agent: uncomment previous line" );
        }


        let paramObject = {
            appId: APP_ID,
            report: _reportType,
            project: _graphReleasePackage,
            life: life,
            serviceName: serviceNameOnly,
            numDays: numDays,
            dateOffSet: reportStartOffset
        };

        if ( _hostNameArray.length == 1 && ( !utils.isSelectedKubernetes() ) ) {
            $.extend( paramObject, {
                host: targetHost
            } );
        }

        console.log( `getServiceReport():  reportUrl: ${ reportUrl }`, paramObject );
        // console.log("getServiceReport: " + report + " days: " + numDays)
        // ;
        $.getJSON(
            reportUrl,
            paramObject )

            .done( function ( responseJson ) {
                showReportAverage( responseJson, reportStartOffset );
                //getServiceReport( 7, _compareOffset );
            } )

            .fail( function ( jqXHR, textStatus, errorThrown ) {
                //reportSuccess( null );
                console.log( "Error: Retrieving report fpr host " + hostName, errorThrown )
                // handleConnectionError( "Retrieving
                // lifeCycleSuccess fpr host
                // " + hostName , errorThrown ) ;
            } );

    }

    function getMetricLabel( performanceMetric ) {
        let osClass = " .stats" + performanceMetric;
        return $( "td:nth-child(1)", osClass );
    }

    function showReportAverage( dataForSelectedPackages, reportStartOffset ) {

        // console.log( " showReportAverage(): " + JSON.stringify( responseJson, null, "\t" ) );

        console.log( " showReportAverage(): dataForSelectedPackages", dataForSelectedPackages );

        let mergedPackageReports = mergeArrayValues( dataForSelectedPackages.data );
        let reportColumn = "td:nth-child(" + ( reportStartOffset + 3 ) + ")";

        if ( $( "tr", $metricsBody ).length == 0 ) {
            // no recent collection available
            build_application_table( new Array(), mergedPackageReports );
            //process_application_report ( dataForSelectedPackages )
        }

        for ( let performanceMetric in mergedPackageReports ) {

            let metricClass = " .stats" + performanceMetric;
            //console.log( "showReportAverage() osClass: " + osClass + " reportColumn:" + reportColumn );
            let reportAverage = getValue( performanceMetric, mergedPackageReports );
            let rawReport = 0;
            if ( mergedPackageReports[ performanceMetric ] && mergedPackageReports.numberOfSamples ) {
                rawReport = Math.round( mergedPackageReports[ performanceMetric ] / mergedPackageReports.numberOfSamples );
            }

            let $labelColumn = getMetricLabel( performanceMetric );
            let rawCurrent = Math.round( $labelColumn.data( "raw" ) / $labelColumn.data( "number" ) );
            let attributePercent = Math.round(
                Math.abs( ( rawCurrent - rawReport ) / rawReport ) * 100 );

            // lots of corner cases...
            if ( rawCurrent <= 1 && reportAverage <= 1 ) {
                attributePercent = 0;
            }

            //			console.log("showReportAverage() current: "  + performanceMetric 
            //					+ " rawCurrent: " + rawCurrent + " rawReport" + rawReport 
            //					+ " attributePercent: " + attributePercent) ;

            let filterThreshold = $( "#filterThreshold" ).val();

            let warningPresent = $( ".resourceWarning", $labelColumn ).length > 0;
            //if ()
            if ( !warningPresent && attributePercent > filterThreshold && reportStartOffset == 0 ) {
                let msg = "Last collected value differs from 24 hour average by " + attributePercent + "%";
                $( "span", $labelColumn )
                    .removeClass()
                    .addClass( "resourceWarning status-yellow" )
                    .attr( "title", msg );
            }
            let $historicalLink = jQuery( '<a/>', {
                class: "simple",
                href: "#ViewHistorical",
                "data-resource": performanceMetric,
                html: reportAverage
            } ).click( function () {

                let urlAction = ANALYTICS_URL
                    + "&graph=" + getGraphId( $( this ) )
                    + "&report=graphService"
                    + "&project=" + utils.getActiveProject()
                    + "&host=" + utils.buildHostsParameter()
                    + "&service=" + serviceName
                    + "&appId=" + APP_ID + "&";

                if ( _reportType.contains( "app" ) ) {
                    urlAction += "appGraph=appGraph&";
                }
                utils.launch( urlAction );

                return false;
            } );
            $( reportColumn, metricClass ).empty();
            $( reportColumn, metricClass ).append( $historicalLink );

        }

        if ( reportStartOffset == 0 ) {
            getServiceReport( 7, _sevenDayOffset );
        }
    }

    // historical hack for some graphs
    function getGraphId( $link ) {
        let graphId = $link.data( "resource" );
        //		if ( graphId.contains( "rssMemory" ) || graphId.contains( "diskUtil" ) )
        //			graphId += "InMB";
        //
        //		if ( graphId.contains( "topCpu" ) )
        //			graphId = "Cpu_15s";

        return graphId;
    }

    // ForAllServicePackages
    function mergeArrayValues( inputArray ) {

        //return inputArray[0] ;

        //console.log( "mergeArrayValues() - merging object count: " + inputArray.length );

        let mergedArray = inputArray[ 0 ];
        for ( let i = 1; i < inputArray.length; i++ ) {
            for ( let attribute in mergedArray ) {
                let mergeValue = mergedArray[ attribute ];
                let iterValue = inputArray[ i ][ attribute ];

                if ( $.isNumeric( mergeValue ) && $.isNumeric( iterValue ) ) {
                    mergedArray[ attribute ] = mergeValue + iterValue;
                    //console.log( attribute + " numeric mergeValue: " + mergeValue + " iterValue: " + iterValue )
                } else {
                    //console.log( attribute + " nonNUMERIC mergeValue: " + mergeValue + " iterValue: " + iterValue )
                }

            }
        }


        return mergedArray;
    }

}