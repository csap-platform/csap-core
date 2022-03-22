// define( [ "browser/utils", "model", "windowEvents", "./dataService", "./trends" ], function ( csapUtils, model, windowEvents, dataService, trends ) {


import _dom from "../../utils/dom-utils.js";


import _csapCommon from "../../utils/csap-common.js" ;

import csapUtils from "../../browser/utils.js"
import model from "../model.js";
import windowEvents from "../windowEvents.js";
import dataService from "./dataService.js";
import trends from "./trends.js";


const summaryTab = summary_tab();

export default summaryTab


function summary_tab() {

    _dom.logHead( "Module loaded" );




    let isDisplayZoomHelpOnce = true;


    let _computeDone = false;
    //let THREAD_REPORT="vm" ; // metricsId=csapThreadsTotal, allVmTotal=true

    let USER_ACTIVITY_REPORT = "vm";
    let HEALTH_REPORT = "custom/health";
    let LOGROTATE_REPORT = "custom/logRotate";
    let CORE_REPORT = "custom/core";
    let CORES_USED = "coresUsed";


    return {
        //
        runSummaryReports: function ( runOnlyIfNeeded ) {

            if ( runOnlyIfNeeded ) {
                if ( !_computeDone )
                    runReports();
            } else {
                runReports();
            }
        },
        //
        updateHeader: function ( coresActive, coresTotal ) {
            updateHeader( coresActive, coresTotal );
        },
        //
    }

    function updateHeader( coresActive, coresTotal ) {
        console.log( "updateHeader() " + coresActive );
        let targetHeader = $( ".head" + CORES_USED );

        if ( $( "#coresActive", targetHeader ).length == 0 ) {
            targetHeader.append( $( "#coreSummaryTemplate" ).html() );

            $( "#coresActive", targetHeader ).text( coresActive.toFixed( 1 ) + " (" + ( coresActive / coresTotal * 100 ).toFixed( 0 ) + "%)" );

            let infraMinimumCores = ( coresActive * 3 * 1.5 ).toFixed( 0 );
            if ( infraMinimumCores < 20 )
                infraMinimumCores = "20"
            $( "#vmRecommend", targetHeader ).text( infraMinimumCores + "Ghz" ); // 3ghz cpu * 1.5 for bursts
        }
    }

    function runReports() {

        let _computeVisible = $( "#coreTrending" ).is( ":visible" );

        // only run if tab is active. jqplot requires dive to be visible.
        if ( !_computeVisible ) {
            console.log( "runComputeReports() - tab not active, skipping" );
            _computeDone = false; // run the report next time it is selected
            return;
        }

        _dom.logHead( "starting summary reports" );

        _computeDone = true;
        $( "#coreTrending" ).empty();

        runSummaryForReport( CORE_REPORT, CORES_USED, "Active Cpu Cores - All Hosts" );
        runSummaryForReport( USER_ACTIVITY_REPORT, "totActivity", "User Activity - All Hosts" );
        runSummaryForReport( HEALTH_REPORT, "UnHealthyCount", "Health Alerts - All Hosts" );

        runSummaryForReport( LOGROTATE_REPORT, "MeanSeconds", "Log Rotation Time (seconds) - All Hosts" );

        // getComputeTrending( THREAD_REPORT , "csapThreadsTotal" , "Application Threads - All VMs") ;

        dataService.runReport( METRICS_HOST );



        if ( $( "#isCustomPerVm" ).is( ':checked' ) ) {
            $( "#vmSummary .entry" ).css( "visibility", "visible" );
        } else {
            $( "#vmSummary .entry" ).css( "visibility", "hidden" );
        }

    }



    function runSummaryForReport( report, selectedColumn, reportLabel ) {

        let targetId = selectedColumn + "ComputePlot";
        //build container first

        let targetFrame = $( "#coreTrending" );

        let computeHead = jQuery( '<div/>', {
            class: "computeHead head" + selectedColumn,
            text: reportLabel
        } );

        targetFrame.append( computeHead );

        let meterDiv = jQuery( '<div/>', {
            id: targetId,
            // 	title: "Trending " + selectedColumn + ": Click on any data point for report",
            class: "metricPlot computePlot"
        } );

        let displayHeight = $( window ).outerHeight( true )
            - $( "header" ).outerHeight( true ) - 300;
        let trendHeight = Math.floor( displayHeight / 3 );
        if ( trendHeight < 250 ) {
            trendHeight = 250;
        }
        meterDiv.css( "height", trendHeight + "px" );

        targetFrame.append( meterDiv );

        let $loadingMessage = jQuery( '<div/>', {
            class: "loading-message-large",
            text: "Building Report: Time taken is proportional to time period selected."
        } );

        meterDiv.append( $loadingMessage );

        // return;

        let paramObject = {
            appId: $( "#appIdFilterSelect" ).val(),
            numDays: $( "#coreTrendingSelect" ).val(),
            project: model.getSelectedProject(),
            metricsId: selectedColumn
        };

        // 

        let life = $( "#lifeSelect" ).val();
        if ( life == null )
            life = uiSettings.lifeParam;
        if ( !$( "#isAllCoreLife" ).is( ':checked' ) ) {
            $.extend( paramObject, {
                life: life
            } );
        }
        if ( $( "#isCustomPerVm" ).is( ':checked' ) && ( $( "#topVmCustom" ).val() != 0 || $( "#lowVmCustom" ).val() != 0 ) ) {
            $.extend( paramObject, {
                perVm: true,
                top: $( "#topVmCustom" ).val(),
                low: $( "#lowVmCustom" ).val()
            } );
        }

        if ( report == USER_ACTIVITY_REPORT ) {
            $.extend( paramObject, {
                "metricsId": "totActivity",
                "trending": true,
                "allVmTotal": true
            } );
        }
        //	
        //	 if ( report == THREAD_REPORT) {
        //		$.extend(paramObject, {
        //			"metricsId": "csapThreadsTotal",
        //			"trending" : true,
        //			"allVmTotal": true,
        //			"divideBy": "numberOfSamples"
        //		});
        //	}

        // custom/core
        let coreUrl = uiSettings.metricsDataUrl + "../report/" + report;
        $.getJSON( coreUrl,
            paramObject )

            .done( function ( responseJson ) {
                buildSummaryReportTrend( selectedColumn, targetId, report, responseJson );

            } )

            .fail(
                function ( jqXHR, textStatus, errorThrown ) {
                    $( "#" + targetId ).text( "Data not available. Ensure the latest agent is installed." );
                    alertify.notify( "Failed getting trending report for: " + report );

                } );


    }


    function buildSummaryReportTrend( selectedColumn, meterDiv, report, currentResults ) {

        console.log( "buildSummaryReportTrend() : " + report );

        let $plotPanel = $( "#" + meterDiv );
        $plotPanel.empty();

        let plotId = `${ meterDiv }-id`;
        let $plotContainer = jQuery( '<div/>', {
            id: plotId
        } );

        $plotContainer.css( "height", Math.round( $plotPanel.outerHeight() - 30 ) + "px" );

        $plotPanel.append( $plotContainer );



        $plotContainer.css( "width",
            Math.round( $plotPanel.outerWidth( false ) ) - csapUtils.jqplotLegendOffset() );


        let currData = currentResults.data[ 0 ]
        if ( currData == undefined || currData[ "date" ] == undefined ) {
            $plotPanel.text( "Report Disabled due to excessive instability. Contact admin to renable once your vms are actively managed" ).css( "height", "2em" );
            console.log( "Trending Data is not available for report: " + report + ". Select an individual service, then run the trending report." );
            return;
        }
        // let seriesToPlot = [  currData[  selectedColumn]  ];
        let seriesToPlot = new Array();
        let seriesLabel = new Array();


        for ( let i = 0; i < currentResults.data.length; i++ ) {

            currData = currentResults.data[ i ];
            //console.log( "buildComputeTrends() Series size: " + currData[  "date"].length) ;


            seriesToPlot.push( trends.buildSeries( currData[ "date" ], currData[ selectedColumn ] ) );
            let curLabel = currData[ "lifecycle" ];

            if ( currData[ "host" ] != undefined )
                curLabel += ":" + currData[ "host" ];
            seriesLabel.push( curLabel );
        }
        // let seriesToPlot = [  buildSeries( currData[  "date"], currData[  selectedColumn])  ];
        // $.jqplot("metricPlot", [[itemValueToday], [itemValueWeek]], {


        _csapCommon.padMissingPointsInArray( seriesToPlot );
        //	console.log( "buildComputeTrends() Plot vals: " +  JSON.stringify(seriesToPlot, null,"\t")  + " labels: " +  JSON.stringify(seriesLabel, null,"\t") ) ;

        let yAxisSettings = {
            min: calculateGraphMin( seriesToPlot )
        };

        if ( $( "#isZeroGraph" ).is( ':checked' ) )
            yAxisSettings = { min: 0 };

        let tomorrow = new Date( new Date().getTime() + 60 * 60 * 24 * 1000 );

        //$container.css("left", "-10px") ;

        const plotOptions = {

            seriesColors: CSAP_THEME_COLORS,

            stackSeries: !$( "#isLineGraph" ).is( ':checked' ),

            seriesDefaults: {
                fill: !$( "#isLineGraph" ).is( ':checked' ),
                fillAndStroke: true,
                fillAlpha: 0.5,
                pointLabels: { //http://www.jqplot.com/docs/files/plugins/jqplot-pointLabels-js.html
                    show: false,
                    ypadding: 0
                },
                rendererOptions: {
                    smooth: true
                },
                markerOptions: {
                    show: true,
                    size: 3,
                    color: "black"
                }
            },

            cursor: {
                show: true,
                tooltipLocation: 'nw',
                zoom: true
            },

            axesDefaults: {
                tickOptions: { showGridline: false },
                labelRenderer: $.jqplot.CanvasAxisLabelRenderer
            },

            axes: {
                xaxis: {
                    // http://www.jqplot.com/tests/date-axes.php

                    //                    max: tomorrow,
                    renderer: $.jqplot.DateAxisRenderer,
                    tickOptions: { formatString: '%b %#d' }
                },
                yaxis: yAxisSettings

            },
            // http://www.jqplot.com/docs/files/plugins/jqplot-highlighter-js.html
            highlighter: {
                show: true,
                showMarker: true,
                sizeAdjust: 20,
                tooltipLocation: "ne",
                tooltipOffset: 5,
                formatString: '%s : %s <br><div class="trendSeries"></div><div class="trendOpen">Click to view daily report</div>',

                tooltipContentEditor: function ( str, seriesIndex, pointIndex, plot ) {
                    //the str is the ready string from tooltipFormatString
                    //depending on how do you give the series to the chart you can use plot.legend.labels[seriesIndex] or plot.series[seriesIndex].label
                    return '<span class=tip-legend>' + plot.legend.labels[ seriesIndex ] + ': </span>' + str;
                }
            },
            legend: {
                labels: seriesLabel,
                placement: "outside",
                show: true
            }
        };

        _dom.logInfo( `-- jqplotting ${ plotId }` );

        // http://www.jqplot.com/docs/files/jqPlotOptions-txt.html
        $.jqplot( plotId, seriesToPlot, plotOptions );

        // smooth out rendering of titile
        //$(".jqplot-xaxis", $container).css("left", "-10px") ;

        $plotContainer.on( 'jqplotClick',
            function ( ev, seriesIndex, pointIndex, data ) {
                if ( isDisplayZoomHelpOnce ) {
                    isDisplayZoomHelpOnce = false;
                    alertify.notify( "double click to reset zoom to original" );
                }
                $plotContainer.off( 'jqplotClick' );
            }
        );
        $plotContainer.on(
            'jqplotDataClick',
            function ( ev, seriesIndex, pointIndex, data ) {

                console.log( `selected: ${ report }: seriesIndex: ${ seriesIndex } ` +
                    "pointIndex: " + pointIndex + " graph: " + data );

                if ( report == HEALTH_REPORT || report == LOGROTATE_REPORT || report == CORE_REPORT ) {

                    let cat = "/csap/reports/health";
                    if ( report == LOGROTATE_REPORT )
                        cat = "/csap/reports/logRotate";
                    if ( report == CORE_REPORT )
                        cat = "/csap/reports/host/daily";

                    let lifeHostArray = ( seriesLabel[ seriesIndex ] ).split( ":" );
                    hostString = "";
                    if ( lifeHostArray.length > 1 )
                        hostString = "&hostName=" + lifeHostArray[ 1 ];

                    let reportUrl = uiSettings.eventApiUrl + "/../..?life=" + lifeHostArray[ 0 ] + hostString
                        + "&category=" + cat
                        + "&date=" + data[ 0 ]
                        + "&project=" + model.getSelectedProject() + "&appId=" + $( "#appIdFilterSelect" ).val();
                    openWindowSafely( reportUrl, "_blank" );
                } else if ( USER_ACTIVITY_REPORT == report ) {
                    let lifeHostArray = ( seriesLabel[ seriesIndex ] ).split( ":" );

                    let reportUrl = uiSettings.analyticsUiUrl + "?report=tableUser&life=" + lifeHostArray[ 0 ]
                        + "&appId=" + $( "#appIdFilterSelect" ).val() + "&project=" + model.getSelectedProject();
                    openWindowSafely( reportUrl, "_blank" );
                }

            } );

        windowEvents.resizeComponents();
    }

    function calculateGraphMin( seriesToPlot ) {
        // set the min
        let lowestValue = -999;

        for ( let i = 0; i < seriesToPlot.length; i++ ) {
            let lineSeries = seriesToPlot[ i ];
            for ( let j = 0; j < lineSeries.length; j++ ) {
                let pointsOnLine = lineSeries[ j ];
                let current = pointsOnLine[ 1 ];
                if ( lowestValue == -999 ) {
                    lowestValue = current;
                } else if ( current < lowestValue ) {
                    lowestValue = current;
                }
            }
        }
        //console.log( "theMinForY pre lower: ", lowestValue) ;
        let loweredBy80Percent = lowestValue * 0.8;
        if ( loweredBy80Percent < 5 ) {
            //            loweredBy80Percent = parseFloat( loweredBy80Percent.toFixed( 1 ) );
            loweredBy80Percent = Math.floor( loweredBy80Percent * 100 ) / 100;

        } else {
            loweredBy80Percent = Math.floor( loweredBy80Percent );
        }



        console.log( "theMinForY: " + lowestValue + " loweredBy80Percent: ", loweredBy80Percent );
        return loweredBy80Percent;
    }


}