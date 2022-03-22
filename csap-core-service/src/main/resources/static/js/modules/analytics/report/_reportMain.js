

// define( [ "model", "windowEvents", "./dataService", "./table", "./utils", "./histogram", "./trends", "./summaryTab" ], function ( model, windowEvents, dataService, table, utils, histogram, trends, summaryTab ) {

import _dom from "../../utils/dom-utils.js";


import windowEvents from "../windowEvents.js";
import dataService from "./dataService.js";


import table from "./table.js";

import histogram from "./histogram.js";
import trends from "./trends.js";
import summaryTab from "./summaryTab.js";


const reports = report_main();

export default reports


function report_main() {

    _dom.logHead( "Module loaded" );




    let _numServiceSample = 0;
    let numReports = 0;
    let _statusFilterTimer;


    return {
        //
        //	function init() {
        initialize: function () {
            console.log( "initializing callbacks" );
            configureDatePickers();
            dataService.setSuccessCallback( reportResponseRouter );
            $( "#isShowJmx" ).change( function () {
                showJmxReport();
            } )


            $( "#filter-service-summary" ).off().keyup( function () {
                console.log( "Applying template filter" );
                clearTimeout( _statusFilterTimer );
                _statusFilterTimer = setTimeout( function () {
                    applyFilter( $( "#filter-service-summary" ) );
                }, 500 );
            } );

            $( "#show-instance-summary" ).change( function () {
                triggerReport( $( this ) );
                setTimeout( function () {
                    applyFilter( $( "#filter-service-summary" ) );
                }, 1000 )
            } );

        },
        // 
        showAllServices: function () {
            dataService.reset();
            let reportTarget = METRICS_OS_PROCESS;
            reportLabel = "OS Resources";
            console.log( "showAllServices(): " + dataService.getLastReportId() )
            //				if ( dataService.getLastReportId() == "jmxCustom/detail" ) {
            //					 reportTarget=METRICS_JAVA ; reportLabel = "Application Resources" ;
            //				} 
            if ( dataService.getLastReportId() == "java/detail" ) {
                reportTarget = METRICS_JAVA;
                reportLabel = "Java Resources";
            }
            $( "#serviceLabel" ).html( reportLabel );
            dataService.runReport( reportTarget );
        },
        //
        resetReportResults: function () {
            dataService.reset();
        },
        //
        getLastService: function () {
            return dataService.getLastService();
        },
        //
        getLastServiceReport: function () {
            return dataService.getLastServiceReport();
        },
        //
        runSummaryReports: function ( runOnlyIfNeeded ) {
            runOnlyIfNeeded = typeof runOnlyIfNeeded !== 'undefined' ? runOnlyIfNeeded : false;
            summaryTab.runSummaryReports( runOnlyIfNeeded );
        },
        //
        hide: function () {

            $( "#reportsSection" ).hide();
            $( "#reportsSection table" ).hide();
        },
        getReport: function ( reportId, optionalServiceName ) {
            dataService.runReport( reportId, optionalServiceName );
        },
        // helper used in model module
        getLastReport: function () {
            dataService.runReport( dataService.getLastReportId() );
        },
        //
        triggerReport: function ( $sourceOfRequest ) {
            triggerReport( $sourceOfRequest );
        }

    };

    function applyFilter( $filter ) {

        let $hostRows = $( 'tbody tr', $( "#serviceSummaryTable" ) );

        let includeFilter = $filter.val();

        console.log( ` includeFilter: ${ includeFilter } ` );


        if ( includeFilter.length > 0 ) {
            $hostRows.hide();
            $filter.css( "background-color", "yellow" );
            let filters = includeFilter.split( "," );
            for ( let filter of filters ) {
                //$( `td:icontains("${ filterArray[i] }")`, $hostRows ).parent().show() ;
                $( `td:nth-child(2)`, $hostRows ).each( function () {
                    let serviceName = $( this ).text();
                    if ( serviceName.includes( filter ) ) {
                        $( this ).parent().show();
                    }
                } );
            }
        } else {
            $hostRows.show();
            $filter.css( "background-color", "" );
        }

    }

    function reportResponseRouter( responseJson, reportId, tableId ) {
        // 
        if ( $( "#visualizeSelect" ).val() != "table" ) {
            // alertify.notify("Visualize") ;
            // $("#compareLabel").show();
            if ( $( "#metricsTrendingSelect" ).val() == 0 ) {
                histogram.loadData( responseJson, reportId );
            } else {
                trends.loadData( responseJson, reportId );
            }
            windowEvents.resizeComponents();
        } else {
            table.loadData( responseJson, reportId, tableId );
        }
    }

    function configureDatePickers() {
        let _datePickerIds = "#reportStartInput, #compareStartInput";
        $( _datePickerIds ).datepicker( {
            maxDate: '0'
        } );
        $( _datePickerIds ).css( "width", "7em" );
        $( _datePickerIds ).change( function () {
            dataService.updateSelectedDates( $( this ) );
        } );
    }

    function triggerReport( $sourceOfRequest ) {

        console.log( `triggerReport() source: '${ $sourceOfRequest.attr( "id" ) }'  Clicking: `
            + dataService.getLastReportId() + " " );
        dataService.reset();
        dataService.updateSelectedDates( $sourceOfRequest );
        if ( $sourceOfRequest.attr( "id" ) == "metricsTrendingSelect" ) {
            console.log( "Triggering trending report" );
        }

        dataService.runReport( dataService.getLastReportId() );
        $( "#clearCompareButton" ).hide();
        if ( dataService.isCompareSelected() ) {
            $( "#clearCompareButton" ).show();
        }
    }

    function showJmxReport() {

        console.log( "showJmxReport()" );
        $( "#visualizeSelect" ).val( "table" )
        let targetReport = METRICS_OS_PROCESS;
        if ( $( "#isShowJmx" ).is( ':checked' ) ) {
            targetReport = "java";
            // console.log("showJmx() _lastReport: " + reportRequest.getLastReportId())
        }

        if ( dataService.getLastReportId().indexOf( "detail" ) != -1 ) {
            targetReport = targetReport + "/detail"
        }

        dataService.runReport( targetReport );
    }


}




