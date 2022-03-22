

// define( [ "browser/utils", "agent/service-live-plots" ], function ( utils, alertPlot ) {
    
    
//     console.log( "Module loaded 99" ) ;


import _dom from "../../utils/dom-utils.js";
import _net from "../../utils/net-utils.js";

import utils from "../utils.js"

import alertPlot from "./service-live-plots.js"


export const serviceLive = Service_Live();


function Service_Live() {

    _dom.logHead( "Module loaded" );


    const $liveContent = $( "#agent-tab-live", "#agent-tab-content" ) ;


    let $metricDetails = $( "#metricDetails", $liveContent ) ;
    let $dataPanel = $( "#data-panel", $liveContent ) ;
    
    
    const $healthStatusSpan = $( "#healthStatus", $liveContent ) ;

    let lastCollectionSeconds = 0 ;

    let podName = "csap-agent" ;
    let isAlarmInfoHidden = false ;
    let isPodProxyMode = false ;

    let $refreshData = $( "#refreshData", $liveContent ) ;
    let $refreshButton = $( "#refreshMetrics", $liveContent ) ;
    let $metricFilter = $( "#metricFilter", $liveContent ) ;
    let $switchTableView = $( "#switch-table-view", $liveContent ) ;
    let _autoTimer = null ;
    let $alertsBody = $( "#alertsBody", $liveContent ) ;
    let $defBody = $( "#defBody", $liveContent ) ;
    let $healthTable = $( "#health", $liveContent ) ;
    let $numberOfHours = $( "#numberHoursSelect", $liveContent ) ;
    let $metricTable = $( "#metricTable", $liveContent ) ;
    let $metricBody = $( "#metricBody", $liveContent ) ;
    let _alertsCountMap = new Object() ;

    let SECOND_MS = 1000 ;
    let MINUTE_MS = 60 * SECOND_MS ;
    let HOUR_MS = 60 * MINUTE_MS ;
    let _filterTimer = null ;
    let _tagFilterTimer = null ;

    let testCountParam ;

    let init ;


    return {
        show: function ( $menuContent, forceHostRefresh, menuPath ) {


            if ( !init ) {
                registerForEvents() ;
            }


            let selectedService = utils.getServiceSelector().val() ;
            if ( selectedService !== utils.agentServiceSelect() ) {
                podName = selectedService ;
            } else {
                podName = "csap-agent" ;
            }
            
            return autoRefreshMetrics() ;

            //setTimeout( autoRefreshMetrics, 30 ) ;

        }

    } ;


    function registerForEvents() {
        init = true ;
        utils.getServiceSelector().change( function () {

            podName = $( this ).val() ;
            autoRefreshMetrics() ;

        } ) ;

        isAlarmInfoHidden = true ;


        $( ".simon-only" ).hide() ;
//        $( ".ui-tabs-nav" ).hide() ;
        $( "#meter-view" ).val( "api" ) ;


        isPodProxyMode = true ;




        $refreshData.change( autoRefreshMetrics ) ;

        $numberOfHours.change( getAlerts ) ;

        $( "#refreshAlerts" ).click( function () {

            getAlerts() ;

        } ) ;

        $switchTableView.click( function () {
            $metricTable.toggle() ;
            $healthTable.toggle() ;
            $metricFilter.toggle() ;
        } )

        let microSortOrder = [ [ 7, 1 ] ] ;
        $( "#meter-view" ).change( function () {


            isAlarmInfoHidden = true ;
            $( ".simon-only" ).hide() ;

            if ( $( this ).val() == "meter-only" ) {
                switchViewToMeterOnly() ;
                $metricTable.trigger( "updateAll" ) ;
                $metricTable.data( 'tablesorter' ).sortList = microSortOrder ;
            } else if ( $( this ).val() == "starter" ) {
//                getDefinitions() ;
                isAlarmInfoHidden = false ;
//                $( ".ui-tabs-nav" ).show() ;
                $( ".simon-only" ).show() ;
                $switchTableView.css( "visibility", "visible" ) ;
                setTimeout( function () {
                    $metricTable.find( 'th:eq(1)' ).trigger( 'sort' ) ;
                }, 1000 ) ;


            }
            $( "#meter-tag-filter" ).hide() ;
            if ( $( this ).val() == "apiAggregated" ) {
                $( "#meter-tag-filter" ).show() ;
            }

            autoRefreshMetrics( true ) ;


        } ) ;

        // alerts will refresh getMicroMeters
        $refreshButton.click( function () {
            if ( isAlarmInfoHidden ) {
                getMicroMeters() ;
            } else {
                getAlerts() ;
            }
        } ) ;

        $( "#clearMetrics" ).click( function () {
            $.getJSON(
                    baseUrl + "/../clearMetrics" )
                    .done( function ( metricResponse ) {

                        alertify.alert( "Experimental: Only non jvm and non tomcat are cleared: " + metricResponse.cleared + " cleared, skipped: " + metricResponse.skipped ) ;
                        getMicroMeters() ;

                    } )

                    .fail( function ( jqXHR, textStatus, errorThrown ) {

                        handleConnectionError( "clearing alerts", errorThrown ) ;
                    } ) ;
        } ) ;


        $metricFilter.keyup( function () {
            // 
            clearTimeout( _filterTimer ) ;
            _filterTimer = setTimeout( function () {
                filterMetrics() ;
            }, 500 ) ;


            return false ;
        } ) ;

        $( "#meter-tag-filter" ).keyup( function () {
            // 
            console.log( `meter filter change` )
            clearTimeout( _tagFilterTimer ) ;
            _tagFilterTimer = setTimeout( function () {
                getMicroMeters() ;
            }, 500 ) ;


            return false ;
        } ) ;

        $.tablesorter.addParser( {
            // set a unique id
            id: 'raw',
            is: function ( s, table, cell, $cell ) {
                // return false so this parser is not auto detected
                return false ;
            },
            format: function ( s, table, cell, cellIndex ) {
                let $cell = $( cell ) ;
                // console.log("timestamp parser", $cell.data('timestamp'));
                // format your data for normalization
                return $cell.data( 'raw' ) ;
            },
            // set type, either numeric or text
            type: 'numeric'
        } ) ;

        $healthTable.tablesorter( {
            sortList: [ [ 0, 1 ] ],
            theme: 'csapSummary'
        } ) ;

        let sortOrder = [ [ 1, 1 ], [ 7, 1 ], [ 2, 1 ] ] ;
        if ( isAlarmInfoHidden ) {
            sortOrder = microSortOrder ;
        }

        $metricTable.tablesorter( {
            sortList: sortOrder,
            stringTo: "bottom",
            emptyTo: 'bottom',
            theme: 'csapSummary'
        } ) ;

        $( "tr", $defBody ).each( function ( index ) {
            let $defRow = $( this ) ;
            let defId = $( ":nth-child(1) span", $defRow ).text().trim() ;
            _alertsCountMap[defId] = 0 ;
        } ) ;


    }


    function autoRefreshMetrics( doNow ) {

        $( "#meter-source", $liveContent ).empty().text( podName ) ;

        let newInterval = $refreshData.val() ;
        clearTimeout( _autoTimer ) ;

        if ( newInterval < 0 && !doNow ) {
            console.log( `disabled autorefresh` ) ;
            return ;
        }

        if ( isAlarmInfoHidden ) {
            getMicroMeters() ;
        } else {
            getAlerts() ;
        }


        _autoTimer = setTimeout( function () {
            autoRefreshMetrics() ;
        }, newInterval * 1000 ) ;
    }

    function busy( show ) {

        if ( show ) {
            $refreshButton.removeClass( "refresh-window" ) ;
            $refreshButton.addClass( "csap-activity" ) ;
        } else {
            $refreshButton.addClass( "refresh-window" ) ;
            $refreshButton.removeClass( "csap-activity" ) ;
        }
    }

    function getMicroMeters() {


        busy( true ) ;
        let apiRequest = $( "#meter-view" ).val() ;
        let params = {
            meterView: apiRequest,
            precision: 3
        } ;

        if ( apiRequest == "starter" ) {
            $.extend( params, {
                "aggregate": true,
                "tagFilter": "csap-collection"
            } ) ;
        }

        if ( apiRequest == "apiDetails" ) {
            $.extend( params, {
                "details": true
            } ) ;
        }
        if ( apiRequest == "apiAggregated" ) {
            $.extend( params, {
                "aggregate": true,
                "tagFilter": $( "#meter-tag-filter" ).val()
            } ) ;
        }

        let microUrl = `/podProxy/${ podName }?csapui=true` ;

        console.log( `=>loading ${ microUrl }` ) ;
        // $.getJSON(
        //         microUrl, params )
        //         .done( processMicroMeters )

        //         .fail( function ( jqXHR, textStatus, errorThrown ) {

        //             handleConnectionError( "getting alerts", errorThrown ) ;
        //         } ) ;

        let viewLoadedPromise = _net.httpGet( microUrl, params );

        viewLoadedPromise
            .then( processMicroMeters )
            .catch( ( e ) => {
                console.warn( e );
            } );;


        return viewLoadedPromise;

    }

    function processMicroMeters( microMeterReport ) {
        //console.log( "processMicroMeters() ", microMeterReport ) ;

        $( "div.meter-errors", $dataPanel ).remove() ;


        if ( microMeterReport.error ) {
            let $error = jQuery( '<div/>', {
                class: "csap-white meter-errors",
                text: `${microMeterReport.error}`
            } ).css( "word-break", "break-all" ).css( "width", "40em" ) ;
            jQuery( '<div/>', {
//                text: microMeterReport.details
                class: "csap-blue",
                text: `verify csap live integration is available and enabled`
            } ).appendTo( $error ) ;

            $error.append( `source: ${ microMeterReport.source }` ) ;

            $metricBody.empty() ;
            $dataPanel.append( $error ) ;
            return ;
        }




        let $updatedData = jQuery( '<tbody/>', { } ) ;
        addMeterRows( microMeterReport, $updatedData ) ;

        // support for "raw" data
        addRawRows( microMeterReport.report, $updatedData ) ;

        $metricBody.html( $updatedData.html() ) ;
        busy( false ) ;

        if ( !$( "th.simon-only", $metricTable ).is( ":visible" ) ) {
            $( "td.simon-only", $metricTable ).hide() ;
        }
        $metricTable.trigger( "update" ) ;



        $( "button.plot-launch", $metricBody ).off().click( function () {
            alertPlot.addPlot( $( this ).data( "name" ) ) ;
        } ) ;
        $( "button.id-launch", $metricBody ).off().click( function () {
            let name = $( this ).data( "name" ) ;
            let message = name + " : " + JSON.stringify( microMeterReport[name], "\n", "\t" ) ;
            alertify.csapInfo( message ) ;
        } ) ;

        alertPlot.drawPlots() ;
        updateCounts() ;
    }

    function addRawRows( report, $updatedData ) {

        for ( let fieldName in report ) {

            let $row = jQuery( '<tr/>', { } ) ;
            $row.appendTo( $updatedData ) ;

            jQuery( '<td/>', {
                text: fieldName
            } ).appendTo( $row ) ;

            //$row.append( buildMicroValue(  ) ) ;


            let $detailsCol = jQuery( '<td/>', {
                colspan: 99
            } ).appendTo( $row ) ;

            let $details = jQuery( '<pre/>', {
                text: JSON.stringify( report[fieldName], "\n", "   " ),
                colspan: 99
            } ).appendTo( $detailsCol ) ;

//            $row.append( buildMicroValue(  ) ) ;
//            $row.append( buildMicroValue(  ) ) ;
//            $row.append( buildMicroValue(  ) ) ;
//            $row.append( buildMicroValue( ) ) ;
//            $row.append( buildMicroValue(  ) ) ;

        }

    }

    function addMeterRows( microMeterReport, $updatedData ) {
        //
        // Get collection delta
        let currentSecondsInterval = 1 ;
        let upTimeSeconds = 1 ;
        for ( let meterName in microMeterReport ) {
            if ( meterName.startsWith( "process.uptime" ) ) {
                alertPlot.addTimeNow() ;
                let meter = microMeterReport[meterName] ;
                currentSecondsInterval = meter - lastCollectionSeconds ;
                lastCollectionSeconds = meter ;
                upTimeSeconds = meter ;
                break ;
            }
        }

        for ( let meterName in microMeterReport ) {
            if ( meterName == "health-report" ) {
                let healthReport = microMeterReport["health-report"] ;
                
                console.log( "healthReport: ", healthReport ) ;
                
                updateStatus( healthReport.isHealthy,
                        healthReport.lastCollected,
                        upTimeSeconds ) ;

                $( "#show-health-issues" )
                        .off()
                        .click( function () {
                            let message = JSON.stringify( healthReport, "\n", "\t" ) ;
                            alertify.csapInfo( message ) ;
                        } )
                        .show() ;
                
                if ( !healthReport.isHealthy && healthReport.errors ) {
                    $healthStatusSpan.append( "(" + healthReport.errors.length + ")" ) ;
                }


                continue ;
            }

            let meter = microMeterReport[meterName] ;
            //console.log(`meter: ${meterName}`) ;
            if ( meterName.startsWith( "process.uptime" )
                    || meterName === "report"
                    || meterName === "timers" ) {
                continue ;
            }

            let $row = jQuery( '<tr/>', { } ) ;
            $row.appendTo( $updatedData ) ;

            let $nameCell = jQuery( '<td/>', { } ) ;
            let $tag = jQuery( '<span/>', { class: "meter-tag" } ) ;
            $row.append( $nameCell ) ;

            let nameFormatted = meterName ;
            let tagIndex = meterName.indexOf( "[" ) ;
            if ( tagIndex > 0 ) {
                nameFormatted = meterName.substr( 0, tagIndex ) ;
                $tag.text( meterName.substr( tagIndex ).replace( /,/g, ', ' ) ) ;
            }

            let $idButton = jQuery( '<button/>', {
                class: "csap-icon csap-info id-launch",
                title: "View collection ids",
                "data-name": meterName
            } ) ;

            let $graphButton = jQuery( '<button/>', {
                class: "csap-icon csap-graph plot-launch",
                title: "Show Graph",
                "data-name": meterName
            } ) ;

            $nameCell.append( $idButton ) ;
            $nameCell.append( $graphButton ) ;
            $nameCell.append( nameFormatted ) ;
            $nameCell.append( $tag ) ;

            //console.log( `meter: ${meterName}, isObject ${ isObject( meter )}`, meter )

            if ( isObject( meter ) ) {
                if ( meter.details ) {
                    if ( meter.details.description ) {
                        let description = meter.details.description ;
                        $nameCell.append( jQuery( '<div/>', {
                            class: "quote",
                            html: description
                        } ) ) ;
                    }

                    if ( meter.details.tags ) {

                        let $tagQuote = jQuery( '<div/>', {
                            class: "quote"
                        } ) ;
                        $nameCell.append(
                                $tagQuote
                                ) ;

                        for ( let tagName in meter.details.tags ) {
                            jQuery( '<span/>', {
                                class: "tag",
                                text: tagName + ": " + meter.details.tags[tagName]
                            } ).appendTo( $tagQuote ) ;
                        }
                    }
                }

                if ( !isAlarmInfoHidden ) {
                    $row.append( buildAlertCell( meterName ) ) ;
                } else {
                    $row.append( jQuery( '<td/>', {
                        class: "simon-only",
                        text: ""
                    } ) ) ;
                }

                let count = meter.count ;
                if ( meter.value ) {
                    count = meter.value ;
                }
                alertPlot.addData( meterName, currentSecondsInterval, count, meter )
                $row.append( buildMicroValue( count ) ) ;

                $row.append( buildMicroValue( meter["mean-ms"], "ms" ) ) ;

                let $snapMean = buildMicroValue( meter["bucket-0.5-ms"], "ms" ) ;
                $row.append( $snapMean ) ;
                let $snap95 = buildMicroValue( meter["bucket-0.95-ms"], "ms" ) ;
                $row.append( $snap95 ) ;
                $row.append( buildMicroValue( meter["bucket-max-ms"], "ms" ) ) ;
                $row.append( buildMicroValue( meter["total-ms"], "ms" ) ) ;
            } else {
                if ( !isAlarmInfoHidden ) {
                    $row.append( buildAlertCell( meterName ) ) ;
                } else {
                    $row.append( jQuery( '<td/>', {
                        class: "simon-only",
                        text: ""
                    } ) ) ;
                }
                alertPlot.addData( meterName, currentSecondsInterval, meter )
                $row.append( buildMicroValue( meter ) ) ;
                $row.append( buildMicroValue( meter.missing ) ) ;
                $row.append( buildMicroValue( meter.missing ) ) ;
                $row.append( buildMicroValue( meter.missing ) ) ;
                $row.append( buildMicroValue( meter.missing ) ) ;
                $row.append( buildMicroValue( meter.missing ) ) ;
            }

            filterMetricRow( $row ) ;
        }

    }

    function buildAlertCell( meterName ) {

        let alertContents = "" ;
        let $alertImage = "" ;
        let alertValue = "" ;

        console.log( `_alertsCountMap: ${meterName}`, _alertsCountMap ) ;
        if ( _alertsCountMap[meterName.trim() ] == 0 ) {

            console.log( `no alerts: ${meterName} ` )
            alertContents = "" ;
            $alertImage = jQuery( '<span/>', {
                text: alertContents,
                class: "status-green"
            } ) ;
            alertValue = 0 ;
        } else if ( _alertsCountMap[meterName.trim() ] > 0 ) {
            console.log( `found alerts: ${meterName} ` )
            alertContents = _alertsCountMap[meterName.trim()] ;
            $alertImage = jQuery( '<span/>', {
                text: alertContents,
                class: "status-red"
            } ) ;
            alertValue = 1 ;
        }


        let $alertCell = jQuery( '<td/>', {
            class: "simon-only",
            "data-raw": alertValue
        } ) ;

        if ( $alertImage != "" ) {
            $alertCell.append( $alertImage ) ;
        }
        return $alertCell ;
    }

    function updateCounts() {

        let $rows = $( "tr", $metricBody ) ;
        let $visibleRows = $( "tr:visible", $metricBody ) ;
        let countMessage = $visibleRows.length ;
        if ( $visibleRows.length != $rows.length ) {
            countMessage += " of " + $rows.length ;
        }
        $( "#meter-count", $liveContent ).text( countMessage ).parent().show() ;
    }

    function buildMicroValue( val, unit ) {
        let showVal = "" ;
        let sortVal = "" ;
        if ( val != undefined ) {
            sortVal = val ;
            let collected = val ;
            if ( $.isNumeric( val ) ) {
                collected = val.toFixed( 1 ) ;
            }
            showVal = collected ;
            if ( unit && unit == "ms" ) {
                showVal = adjustTimeUnitFromMs( collected )
            } else {
                if ( ( showVal + "" ).endsWith( ".0" ) ) {
                    //showVal = '<span class="padZero">' + val.toFixed(0) + "</span>"
                    //showVal =  val.toFixed(0) ;
                    showVal = numberWithCommas( val )
                }
            }
        }

        let $cell = jQuery( '<td/>', {
            html: showVal,
            "data-raw": sortVal
        } )
        return $cell ;
    }

    function adjustTimeUnitFromMs( collected ) {

        let showVal = collected + "<span>ms</span>" ;
        if ( collected > 24 * HOUR_MS ) {
            showVal = ( collected / 24 / HOUR_MS ).toFixed( 1 ) + "<span>days</span>" ;
        } else if ( collected > HOUR_MS ) {
            showVal = ( collected / HOUR_MS ).toFixed( 1 ) + "<span>hrs</span>" ;
        } else if ( collected > MINUTE_MS ) {
            showVal = ( collected / MINUTE_MS ).toFixed( 1 ) + "<span>min</span>" ;
        } else if ( collected > ( SECOND_MS ) ) {
            showVal = ( collected / SECOND_MS ).toFixed( 2 ) + "<span>s</span>" ;
        }
        return showVal ;
    }

    function isObject( theReference ) {

        if ( theReference == null )
            return false ;

        return typeof theReference === "object" ;
    }

    function updateStatus( isHealthy, lastCollected, uptimeSeconds ) {
        
        
         $healthStatusSpan.removeClass( `status-green status-red` ) ;
         
        $healthStatusSpan.empty() ;
        let status = `status-green` ;
        if ( !isHealthy ) {
            status = 'status-red' ;
        }

        $healthStatusSpan.parent().attr( "title", "last refreshed: " + lastCollected ) ;

        $healthStatusSpan.addClass( status ) ;

        let uptimeWithUnit = adjustTimeUnitFromMs( uptimeSeconds * 1000 ) ;
        $( "#uptime" ).html( ` up: ${uptimeWithUnit}` ) ;
    }


    function getMetricItem( name ) {
        let params = {
            "name": name,
            meterView: $( "#meter-view" ).val()
        } ;
        $.getJSON(
                baseUrl + "/../metric", params )
                .done(
                        showMetricDetails )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    handleConnectionError( "clearing alerts", errorThrown ) ;
                } ) ;
    }


    function showMetricDetails( metricResponse ) {

        $( ".name", $metricDetails ).text( metricResponse.name ) ;
        $( "#firstTime", $metricDetails ).text( metricResponse.firstUsage ) ;
        $( "#lastTime", $metricDetails ).text( metricResponse.lastUsage ) ;
        $( "#maxTime", $metricDetails ).text( metricResponse.maxTimeStamp ) ;

        if ( !metricResponse.details ) {
            alertify.alert( "Details not found" ) ;
            return ;
        }
        let detailItems = metricResponse.details.split( "," ) ;

        let $tbody = $( "tbody", $metricDetails ) ;
        $tbody.empty() ;

        for ( let i = 0 ; i < detailItems.length ; i++ ) {

            let $row = jQuery( '<tr/>', { } ) ;
            $row.appendTo( $tbody ) ;

            let items = detailItems[i].split( "=" ) ;

            if ( items[0].contains( "name" ) || items[0].contains( "note" ) )
                continue ;

            jQuery( '<td/>', {
                text: items[0]
            } ).appendTo( $row ) ;
            jQuery( '<td/>', {
                text: items[1]
            } ).appendTo( $row ) ;
        }




        alertify.alert( $metricDetails.html() ) ;

    }



    function numberWithCommas( collectedValue ) {
        let displayValue = collectedValue ;
        if ( ( collectedValue + "" ).endsWith( ".0" ) ) {
            displayValue = collectedValue.toFixed( 0 ) ;
        }
        displayValue = displayValue.toString().replace( /\B(?=(\d{3})+(?!\d))/g, "," ) ;
        return displayValue ;
    }

    function filterMetrics() {

        $( "tr", $metricBody ).each( function ( index ) {
            filterMetricRow( $( this ) ) ;
        } ) ;

        updateCounts() ;

    }

    function filterMetricRow( $row ) {

        let $nameCell = $( "td:first-child", $row ) ;

        let simonFilter = $( "#metricFilter" ).val().trim().toLowerCase() ;
        //console.debug( "applying filter: ", simonFilter ) ;

        let simonName = $nameCell.text().toLowerCase() ;
        let $nameParent = $nameCell.parent() ;
        if ( simonFilter.length > 0 && simonName.indexOf( simonFilter ) == -1 ) {
            $nameParent.hide() ;
        } else {
            $nameParent.show() ;
        }
    }

    function getAlerts() {

        busy( true ) ;

        let alertUrl = baseUrl + "/alerts" ;

        let paramObject = {
            hours: $numberOfHours.val()
        } ;

        if ( isPodProxyMode ) {

            if ( podName == "default" ) {
                alertUrl = baseUrl + "/../metrics/micrometers" ;
            } else {
                // csap pod connection
                alertUrl = `/podProxy/${ podName }` ;
            }
            paramObject = {
                alertReport: true,
                hours: $numberOfHours.val()
            } ;
        }

        if ( testCountParam ) {
            $.extend( paramObject, {
                testCount: testCountParam
            } ) ;
        }

        console.log( `loading alarms ${ alertUrl }` ) ;

        $.getJSON( alertUrl, paramObject )
                .done( function ( alertResponse ) {

                    busy( false ) ;
                    ;
                    //console.log( "alertResponse", alertResponse ) ;


                    $alertsBody.empty() ;
                    let alerts = alertResponse.triggered ;
                    if ( alerts.length == 0 ) {
                        let $row = jQuery( '<tr/>', { } ) ;

                        $row.appendTo( $alertsBody ) ;

                        $row.append( jQuery( '<td/>', {
                            colspan: 99,
                            text: "No alerts found. Adjust filters as needed."
                        } ) )
                    } else {
                        addAlerts( alerts ) ;
                    }

                    $healthTable.trigger( "update" ) ;
                    getMicroMeters() ;

                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    handleConnectionError( "getting alerts", errorThrown ) ;
                } ) ;

    }

    function addAlerts( alerts ) {

        for ( let id in _alertsCountMap ) {
            _alertsCountMap[id] = 0 ;
        }
        for ( let i = 0 ; i < alerts.length ; i++ ) {
            let $row = jQuery( '<tr/>', { } ) ;

            let alert = alerts[i]

            $row.appendTo( $alertsBody ) ;

            jQuery( '<td/>', {
                text: alert.time,
                "data-raw": alert.ts
            } ).appendTo( $row ) ;

            jQuery( '<td/>', {
                text: alert.id
            } ).appendTo( $row ) ;

            if ( !_alertsCountMap[alert.id] ) {
                _alertsCountMap[alert.id] = 0 ;
            }

            _alertsCountMap[alert.id] = _alertsCountMap[alert.id] + 1 ;

            jQuery( '<td/>', {
                text: alert.type
            } ).appendTo( $row ) ;

            let desc = alert.description ;
            if ( alert.count > 1 ) {
                desc = desc + "<br/><div>Alerts Throttled: <span>" + alert.count + "</span></div>" ;
            }
            jQuery( '<td/>', {
                html: desc
            } ).appendTo( $row ) ;
        }

        if ( alerts.length > 0 ) {
            _alertsCountMap["csap.health.report.fail"] = alerts.length ;
        } else {
            _alertsCountMap["csap.health.report.fail"] = 0 ;
        }
    }
}