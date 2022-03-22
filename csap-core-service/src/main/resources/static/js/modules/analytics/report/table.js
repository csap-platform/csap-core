// define( [ "model", "windowEvents", "./dataService", "./utils", "./tableUtils", "./summaryTab", "./table_OS", "./table_JMX", ], function ( model, windowEvents, dataService, utils, tableUtils, summaryTab, osTable, jmxTable ) {

import _dom from "../../utils/dom-utils.js";

import CsapUser from "../../utils/CsapUser.js"


import model from "../model.js";
import windowEvents from "../windowEvents.js";
import dataService from "./dataService.js";

import utils from "./utils.js";
import tableUtils from "./tableUtils.js";
import summaryTab from "./summaryTab.js";
import osTable from "./table_OS.js";
import jmxTable from "./table_JMX.js";


const table = table_main();

export default table


function table_main() {

    _dom.logHead( "Module loaded" );


    console.log( "Module loaded: reports/table" );
    let csapUser = new CsapUser();
    let metricParamDoneOnce = false;

    let resizeDelayForRenderingRaceCondition = 500;

    return {
        //
        loadData: function ( responseJson, reportId, tableId ) {
            buildTable( responseJson, reportId, tableId );
        },
        //
    }

    function buildTable( responseJson, reportId, tableId ) {

        console.log( `buildTable() - reportId: ${ reportId }, tableId: ${ tableId } ` );

        let bodyId = tableId + " tbody";
        $( "#emailText" ).text( "" );
        let numSamples = 0;
        let reportRows = responseJson.data;

        if ( !reportRows || ( reportRows.length == 0 ) ) {
            alertify.alert( "No data found. Contact service developers to request they publish productivity data.<br><br>" +
                "<a class='simple' href='https://github.com/csap-platform/csap-core/wiki#updateRefCustom+Metrics'>Reference Guide</a><br><br>" );
        }

        let coresActive = 0;

        let coresTotal = 0;
        let isBuildSelectOptions = true;
        for ( let reportRow of reportRows ) {

            if ( isBuildSelectOptions ) {
                buildVisualizeSelect( reportRow );
                isBuildSelectOptions = false;
            }

            let tableRow = jQuery( '<tr/>', {} );
            if ( reportId == "userid" ) {

                buildUseridRow( responseJson, tableRow, reportRow );

            } else if ( reportId == METRICS_OS_PROCESS ) {

                let serviceName = reportRow.serviceName
                if ( !model.isServiceInSelectedCluster( serviceName ) )
                    continue;

                osTable.buildSummaryRow( responseJson, tableRow, reportRow );

            } else if ( reportId == `${ METRICS_OS_PROCESS }/detail` ) {

                osTable.buildServiceDetailRow( responseJson, tableRow, reportRow );

            } else if ( reportId == METRICS_JAVA ) {

                jmxTable.buildSummaryRow( responseJson, tableRow, reportRow );

            } else if ( reportId == `${ METRICS_JAVA }/detail` ) {

                jmxTable.buildDetailRow( responseJson, tableRow, reportRow );

            } else if ( reportId == `${ METRICS_APPLICATION }/detail` ) {

                jmxTable.buildCustomRow( responseJson, tableRow, reportRow );

            } else if ( reportId == METRICS_HOST ) {

                let rowHost = reportRow.hostName
                if ( ! model.isHostInSelectedCluster( rowHost ) )
                    continue;

                let totalCpu = ( reportRow.totalUsrCpu + reportRow.totalSysCpu ) / reportRow.numberOfSamples;
                let coresUsed = totalCpu * reportRow.cpuCountAvg / 100;
                coresActive += coresUsed;
                coresTotal += reportRow.cpuCountAvg;
                // console.log(" totalCpu " + totalCpu + "  coresUsed: " + coresUsed ) ;

                buildHostRow( tableRow, reportRow );

            } else {
                console.error( `buildTable() - unexpected reportId: ${ reportId } ` );
            }

            $( bodyId ).append( tableRow )


            if ( reportId == "userid" ) {
                numSamples += 1;
            } else {
                let metaFields = [ "project", "serviceName", "appId", "lifecycle", "numberOfSamples", ];
                let reportArray = Object.keys( reportRow );
                numSamples += reportRow.numberOfSamples * ( reportArray.length - metaFields.length );
            }


        }

        if ( reportId == METRICS_HOST ) {
            // vm data is used to load summary header
            summaryTab.updateHeader( coresActive, coresTotal );
        }

        $( "#visualizeSelect" ).sortSelect( "data-sorttext" );
        //        $( "#visualizeSelect" ).selectmenu( "refresh" ) ;

        utils.updateSampleCountDisplayed( numSamples );

        if ( $( "#isAllProjects" ).is( ':checked' ) ) {
            $( " .projectColumn" ).css( "display", "table-cell" );
        } else {
            $( " .projectColumn" ).hide();
        }

        $( tableId ).show();
        $.tablesorter.computeColumnIndex( $( "tbody tr", tableId ) );
        //$( tableId ).trigger( "tablesorter-initialized" ) ; // needed for math plugin
        $( tableId ).trigger( "update" );
        //console.warn( `refreshing: ${tableId}` ) ;

        $( ".columnSelector" ).hide();
        let filterdId = reportId.replace( /\//g, "" );
        console.log( "filterdId: " + filterdId );
        $( "#" + filterdId + "ColumnSelector" ).show();

        if ( reportId == "userid" ) {
            let delay = 500;
            csapUser.onHover( $( "#useridTable tbody tr td:nth-child(1)" ), delay );
        }
        $( "#reportsSection" ).show();

        if ( dataService.isCompareSelected() ) {
            let message = "Number of Matches: " + $( ".diffHigh" ).length;
            message += '<span style="font-size: 0.8em; padding-left: 3em">Minimum Value Filter: <label>'
                + $( "#compareMinimum" ).val();
            message += "</label> Hide non matches: <label>"
                + $( "#isCompareRemoveRows" ).prop( "checked" )
            message += "</label> Difference percent: <label>"
                + $( "#compareThreshold" ).val() + "</label>"
            message += "Max Days: <label>" + dataService.getLastReportResults().numDaysAvailable
                + "</label></span>"

            let msgDiv = jQuery( '<div/>', {
                class: "settings compMessage",
                id: "compareCurrent",
                html: message

            } );

            $( "#reportOptions" ).append( msgDiv );

            // $("#reportOptions").append(msgDiv);

            if ( $( "#isCompareRemoveRows" ).prop( "checked" ) ) {
                $( ".diffHigh" ).parent().parent().addClass( "diffHighRow" );
                $( "tr:not(.diffHighRow)", bodyId ).remove();
            }

            if ( $( "#isCompareEmptyCells" ).prop( "checked" ) ) {
                $( ".diffHigh" ).parent().addClass( "diffHighCell" );
                $( ".col1", bodyId ).addClass( "diffHighCell" );
                $( "td:not(.diffHighCell)", bodyId ).empty();
            }


            //$( tableId ).trigger( "tablesorter-initialized" ) ; // otherwise hidden rows come back
            $( tableId ).trigger( "update" );


        }

        if ( dataService.getLastReportResults() != null ) {

            let daysCollected = dataService.getLastReportResults().numDaysAvailable;
            console.log( `dataService.getLastReportResults daysCollected: ${ daysCollected }` );

            $( "#reportStartInput, #compareStartInput" ).datepicker( "option", {
                minDate: 0 - daysCollected
            } );
        } else {
            console.log( `last report is null - skipping datepicker` );
        }

        // induced by tablesorter?
        setTimeout( function () {
            windowEvents.resizeComponents();
            tableUtils.showHighestInColumn( tableId );

            // fix table corners
            $( "th:visible:first", tableId ).addClass( "tableTopLeft" );
            $( "th:visible:last", tableId ).addClass( "tableTopRight" );
            $( "tbody tr:last td:last", tableId ).css( "border-bottom-right-radius", "0px" );


            if ( !metricParamDoneOnce && $.urlParam( "metric" ) != null ) {
                metricParamDoneOnce = true;
                setTimeout( function () {
                    $( "#visualizeSelect" ).val( $.urlParam( "metric" ) );
                    $( "#visualizeSelect" ).trigger( "change" );

                    //                    $( "#visualizeSelect" ).selectmenu( "refresh" ) ;
                    //alertify.alert("new timer") ;
                }, 1000 );
            }

        }, resizeDelayForRenderingRaceCondition );
        resizeDelayForRenderingRaceCondition = 10;
    }

    function buildVisualizeSelect( reportRow ) {


        console.log( `Building Selection dropdown : `, reportRow );

        let visVal = $( "#visualizeSelect" ).val();

        // For testing graphs
        // visVal = "totalUsrCpu,totalSysCpu";
        // visVal = "cpuCountAvg";

        $( "#visualizeSelect" ).empty();
        let optionItem = jQuery( '<option/>', {
            value: "table",
            "data-sorttext": "___",
            text: " Summary Table "
        } );
        $( "#visualizeSelect" ).append( optionItem );

        // Fill in view options

        for ( let columnName in reportRow ) {

            if ( !$.isNumeric( reportRow[ columnName ] ) )
                continue;

            let label = model.getServiceLabels( reportRow.serviceName, columnName );


            //console.log("desc: ", desc);

            let optionItem = jQuery( '<option/>', {
                value: columnName,
                "data-sorttext": label,
                text: label
            } );

            $( "#visualizeSelect" ).append( optionItem );

            //console.log("peter item: ", item) ;
            if ( columnName == "totalUsrCpu" ) {
                let optionItem = jQuery( '<option/>', {
                    value: columnName + ",totalSysCpu",
                    "data-sorttext": "CPU: mpstat usr + sys",
                    text: "CPU: mpstat usr + sys"
                } );

                $( "#visualizeSelect" ).append( optionItem );
            }
        }
        $( "#visualizeSelect" ).val( visVal );
    }


    function buildHostRow( tableRow, hostReport ) {


        tableRow.append( jQuery( '<td/>', {
            class: "projectColumn",
            text: hostReport.project + " - " + hostReport.lifecycle
        } ) );

        tableRow.append( tableUtils.buildHostLinkColumn( hostReport.hostName ) );

        tableUtils.addCell( tableRow, hostReport, "numberOfSamples", 0 );
        tableUtils.addCell( tableRow, hostReport, "memoryInMbAvg", 1, 1024 );
        tableUtils.addCell( tableRow, hostReport, "swapInMbAvg", 1, 1024 );
        tableUtils.addCell( tableRow, hostReport, "totalMemFree", 1, 1024 );

        tableUtils.addCell( tableRow, hostReport, "totalIo", 0, 1, "%" );
        tableUtils.addCell( tableRow, hostReport, "combinedCpu", 1, 1, "%" );
        tableUtils.addCell( tableRow, hostReport, tableUtils.getFieldSummaryAppendix( "totActivity" ), 0 );

        tableUtils.addCell( tableRow, hostReport, "totalUsrCpu", 0, 1, "%" );
        tableUtils.addCell( tableRow, hostReport, "totalSysCpu", 0, 1, "%" );

        tableUtils.addCell( tableRow, hostReport, "cpuCountAvg", 0 );
        tableUtils.addCell( tableRow, hostReport, "totalLoad", 1, 1, "",
            hostReport.cpuCountAvg / 2 );

        let alertSuffix = "%";
        if ( $( "#isUseTotal" ).is( ':checked' ) )
            alertSuffix = "";

        tableUtils.addCell( tableRow, hostReport, "alertsCount", 1, 0.01, alertSuffix );

        tableUtils.addCell( tableRow, hostReport, "csapThreadsTotal", 0, 1, "", 1500 );
        tableUtils.addCell( tableRow, hostReport, "threadsTotal", 0, 1, "", 2000 );

        tableUtils.addCell( tableRow, hostReport, "socketTotal", 0, 1, "", 300 );
        tableUtils.addCell( tableRow, hostReport, "socketWaitTotal", 0, 1, "", 40 );

        tableUtils.addCell( tableRow, hostReport, "totalNetworkReceived", 0, 1, "" );
        tableUtils.addCell( tableRow, hostReport, "totalNetworkTransmitted", 0, 1, "" );

        tableUtils.addCell( tableRow, hostReport, "csapFdTotal", 0, 1, "", 10000 );
        tableUtils.addCell( tableRow, hostReport, "fdTotal", 0, 1, "", 10000 );
        tableUtils.addCell( tableRow, hostReport, "totalCpuTestTime", 1, 1, "", 5 );
        tableUtils.addCell( tableRow, hostReport, "totalDiskTestTime", 1, 1, "", 5 );

        tableUtils.addCell( tableRow, hostReport, "totalsda", 1, 1, "%" );
        tableUtils.addCell( tableRow, hostReport, "totalIoReads", 1, 1, "", 5 );
        tableUtils.addCell( tableRow, hostReport, "totalIoWrites", 1, 1, "", 5 );
        //console.log("adding row", rowJson ) ;

    }


    function buildUseridRow( responseJson, tableRow, rowJson ) {

        if ( rowJson.uiUser == null ) {
            rowJson.uiUser = "null";
        }

        let theUser = rowJson.uiUser.toLowerCase();
        if ( theUser.contains( "system" ) || theUser.contains( ".gen" ) || theUser.contains( "agentuser" ) || theUser.contains( "null" ) || theUser.contains( "csagent" ) ) {
            console.log( "skipping ", theUser );
            return;
        }

        $( "#emailText" ).append( rowJson.uiUser + "@yourcompany.com;" );

        let col1 = jQuery( '<td/>', {
            class: "col1 userids",
            text: rowJson.uiUser
        } );
        // col1.html(userLink) ;

        let eventLink = jQuery( '<a/>', {
            class: "simple",
            target: "_blank",
            href: utils.getUserUrl( "userid=" + rowJson.uiUser ),
            text: rowJson.totActivity
        } );
        let col2 = jQuery( '<td/>', {
            class: "numeric"
        } );
        col2.html( eventLink );

        // console.log("buildUseridReport: " + col1.text() )

        tableRow.append( col1 ).append( col2 );
    }


}