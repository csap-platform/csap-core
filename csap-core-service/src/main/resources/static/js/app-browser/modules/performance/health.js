define( [ "browser/utils" ], function ( utils ) {

    console.log( "Module loaded" ) ;
    const $fullContainer = $( "#performance-tab-health-reports" ) ;

    const $healthReportFilter = $( "#health-report-filter", $fullContainer ) ;
    let _healthFilterTimer ;

    const $healthPanel = $( "#health-reports", $fullContainer ) ;
    const $settingsBody = $( "#settingBody", $healthPanel ) ;
    const $sourceBody = $( "#sourceBody", $healthPanel ) ;



    let $contentLoaded ;

    const testCountParam = $.urlParam( "testCount" ) ;

    let $filterCounts = $( "#filterCounts" ) ;
    let $alertsBody = $( "#alertsBody" ) ;
    let $defBody = $( "#defBody" ) ;
    let $healthTable = $( "#health" ) ;
    let $numberOfHours = $( "#numberHoursSelect" ) ;

    let _alertsCountMap = new Object() ;

    let SECOND_MS = 1000 ;
    let MINUTE_MS = 60 * SECOND_MS ;
    let HOUR_MS = 60 * MINUTE_MS ;
    let _refreshTimer = null ;


    let applyFilterFunction ;

    return {

        initialize: function () {
            initialize() ;
        },

        show: function ( $displayContainer, forceHostRefresh ) {
            $contentLoaded = new $.Deferred() ;

            getAlerts() ;

            getSettings() ;

            return $contentLoaded ;
        }

    } ;

    function initialize() {
        //$( "#tabs" ).tabs() ;
        $( "#health-tabs", $healthPanel ).tabs( {
            activate: function ( event, ui ) {

                console.log( "Loading: " + ui.newTab.text() ) ;

            }
        } ) ;



        applyFilterFunction = utils.addTableFilter( $healthReportFilter, $healthTable ) ;


        $numberOfHours.change( getAlerts ) ;

        $( "#refreshAlerts" ).click( function () {
            getAlerts()
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


        $( "tr", $defBody ).each( function ( index ) {
            let $defRow = $( this ) ;
            let defId = $( ":nth-child(1)", $defRow ).text().trim() ;
            _alertsCountMap[defId] = 0 ;
        } ) ;


    }

    function getSettings() {


        let parameters = { } ;


        $.getJSON(
                APP_BROWSER_URL + "/health/settings", parameters )
                .done(
                        function ( settingsReport ) {
                            console.log( "settingsReport", settingsReport.settings ) ;
                            showSettings( settingsReport.settings ) ;

                            showSources( settingsReport.healthUrlsByServiceByInstance ) ;

                        } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    handleConnectionError( "getting alerts", errorThrown ) ;
                } ) ;

    }

    function showSettings( settingReport ) {

        $settingsBody.empty() ;

        let settings = utils.keysSortedCaseIgnored( settingReport ) ;

        for ( let setting of settings ) {

            let $row = jQuery( '<tr/>', { } ) ;
            jQuery( '<td/>', {
                "text": setting
            } ).appendTo( $row ) ;

            jQuery( '<td/>', {
                "text": settingReport[ setting ]
            } ).appendTo( $row ) ;

            $settingsBody.append( $row ) ;
        }



    }


    function showSources( healthUrlsByServiceByInstance ) {

        $sourceBody.empty() ;

        let serviceNames = utils.keysSortedCaseIgnored( healthUrlsByServiceByInstance ) ;

        for ( let serviceName of serviceNames ) {

            let $row = jQuery( '<tr/>', { } ) ;
            jQuery( '<td/>', {
                "text": serviceName
            } ).appendTo( $row ) ;



            let $sourceColumn = jQuery( '<td/>', { } ) ;
            $sourceColumn.appendTo( $row ) ;

            let $container = jQuery( '<div/>', {
                class: "source-locations"
            } ) ;

            $sourceColumn.append( $container ) ;

            let hostToUrl = healthUrlsByServiceByInstance[ serviceName ] ;
            let hostNames = utils.keysSortedCaseIgnored( hostToUrl ) ;
            for ( let hostName of hostNames ) {
                jQuery( '<a/>', {
                    text: hostName,
                    class: "csap-link",
                    href: hostToUrl[hostName]
                } ).appendTo( $container ) ;
            }



            $sourceBody.append( $row ) ;
        }



    }

    function getAlerts() {


        let paramObject = {
            hours: $numberOfHours.val()
        } ;

        if ( testCountParam ) {
            $.extend( paramObject, {
                testCount: testCountParam
            } ) ;
        }

        $.getJSON(
                HEALTH_REPORT_URL, paramObject )
                .done(
                        function ( alertResponse ) {
                            console.log( "alertResponse", alertResponse ) ;

                            $contentLoaded.resolve() ;

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
                                for ( let id in _alertsCountMap ) {
                                    _alertsCountMap[id] = 0 ;
                                }
                                console.time( 'updatingAlertsTable' ) ;
                                addAlerts( alerts, 0 ) ;
                                console.timeEnd( 'updatingAlertsTable' ) ;
                            }


                            $( "span:nth-child(1)", $filterCounts ).text( alertResponse.filterTotal ) ;
                            $( "span:nth-child(2)", $filterCounts ).text( alertResponse.storeTotal ) ;
                            
                            
                           


                        } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    handleConnectionError( "getting alerts", errorThrown ) ;
                } ) ;

    }

    // add 100 at a time to improve ui responsiveness
    function addAlerts( alerts, offset ) {

        setTimeout( function () {
            let isComplete = true ;
            for ( let i = offset ; i < alerts.length ; i++ ) {
                //$alertsBody.append( newRows[i] );
                $alertsBody.append( buildRow( alerts[i] ) ) ;
                if ( ( i - offset ) > 200 ) {
                    isComplete = false ;
                    addAlerts( alerts, i + 1 ) ;
                    break ;
                }
            }
            //console.log("All done: " + newRows.length ) ;
            if ( isComplete ) {

                console.log( "triggering update" ) ;
                $healthTable.trigger( "update" ) ;
                setTimeout( applyFilterFunction, 200) ;
            }
        }, 10 ) ;


        //$alertsBody.html( $updatedAlertsBody.html() ) ;

        if ( alerts.length > 0 ) {
            _alertsCountMap["csap.health.report.fail"] = alerts.length ;
        } else {
            _alertsCountMap["csap.health.report.fail"] = 0 ;
        }


    }

    function buildRow( alert ) {
        let $row = jQuery( '<tr/>', { } ) ;


        jQuery( '<td/>', {
            text: alert.time,
            "data-raw": alert.ts
        } ).appendTo( $row ) ;



        let $serviceCell = jQuery( '<td/>', { } ) ;
        $serviceCell.appendTo( $row ) ;

        let $serviceLink = jQuery( '<a/>', {
            target: "_blank",
            title: "Open Health Portal for Service",
            class: "simple",
            href: alert.healthUrl,
            text: alert.service
        } ).appendTo( $serviceCell ) ;




        let $hostCell = jQuery( '<td/>', { } ) ;
        $hostCell.appendTo( $row ) ;

        utils.buildAgentLink( alert.host ).appendTo( $hostCell ) ;

//        let hostUrl = agentHostUrlPattern.replace( /CSAP_HOST/g, alert.host )
//                + "/app-browser" ;
//
//        let $hostPortalLink = jQuery( '<a/>', {
//            target: "_blank",
//            title: "Open Host Portal",
//            class: "simple",
//            href: hostUrl,
//            text: alert.host
//        } ).appendTo( $hostCell ) ;

        let alertId = alert.id ;
        alertId = alertId.replace( /\./g, ".<WBR>" )
        jQuery( '<td/>', {
            html: alertId
        } ).appendTo( $row ) ;

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

        return $row ;
    }

} ) ;