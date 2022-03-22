// define( [ "model", "browser/utils", ], function ( model, csapUtils ) {



import _dom from "../../utils/dom-utils.js";

import csapUtils from "../../browser/utils.js"


const trends = report_trends();

export default trends


function report_trends() {

    _dom.logHead( "Module loaded" );




    let isDisplayZoomHelpOnce = true;

    return {
        //
        loadData: function ( currentResults, reportId ) {
            generateAttributeTrending( currentResults, reportId );
        },
        //
        buildSeries: function ( xArray, yArray ) {
            return buildSeries( xArray, yArray );
        },
        //

    }

    // Critical - in order for plots to resolve - tab must be active


    function generateAttributeTrending( currentResults, reportId ) {

        let userSelectedAttributes = $( "#visualizeSelect" ).val().split( "," );

        let primaryAttribute = userSelectedAttributes[ 0 ];

        if ( userSelectedAttributes.length > 1 ) {
            primaryAttribute = "total";
        }

        let labelArray = new Array();


        _dom.logHead( `generateAttributeTrending() - reportId: ${ reportId } , primaryAttribute: ${ primaryAttribute }` );

        let currData = currentResults.data[ 0 ]
        if ( currData == undefined || currData[ "date" ] == undefined ) {
            $( "#" + meterDiv ).text( "Report Disabled due to excessive instability. Contact admin to renable once your vms are actively managed" ).css( "height", "2em" );
            alertify.error( "Trending Data is not available for report: " + report + ". Select an individual service, then run the trending report." );
            return;
        }


        let $plotContainer = $( "#hostDiv .metricHistogram" );



        if ( reportId !== "host" ) {
            $plotContainer = $( "#os-processDiv .metricHistogram" );
        }
        if ( reportId === "userid" ) {
            $plotContainer = $( "#useridDiv .metricHistogram" );
        }


        $plotContainer.append( $( "#metricsTrendingContainer" ) );
        $( "#metricsTrendingContainer" ).show();


        $plotContainer.show();


        let displayHeight = $( window ).outerHeight( true )
            - $( "#reportOptions" ).outerHeight( true )
            - $( "header" ).outerHeight( true ) - 400;
        let trendHeight = Math.floor( displayHeight );

        let trendWidth =
            Math.round( $plotContainer.outerWidth( false ) ) - csapUtils.jqplotLegendOffset();

        let meterDiv = jQuery( '<div/>', {
            id: "metricPlot",
            title: "Trending " + primaryAttribute,
            class: "meterGraph"
        } )
            .css( "height", trendHeight + "px" )
            .css( "width", trendWidth + "px" );

        $plotContainer.append( meterDiv );


        let seriesToPlot = new Array();
        let seriesLabel = new Array();

        for ( let i = 0; i < currentResults.data.length; i++ ) {

            currData = currentResults.data[ i ];
            //console.log( "buildComputeTrends() Series size: " + currData[  "date"].length) ;


            seriesToPlot.push( buildSeries( currData[ "date" ], currData[ primaryAttribute ] ) );
            let curLabel = currData[ "lifecycle" ];

            if ( currData[ "host" ] != undefined )
                curLabel += ":" + currData[ "host" ];
            seriesLabel.push( curLabel );

            // service/detail and jmx detail both need label
            if ( i == 0 && reportId.contains( "detail" ) ) {
                $( "#serviceLabel" ).html(
                    currData.serviceName + " : " + primaryAttribute );
            }
        }


        let yAxisSettings = { min: calculateGraphMin( seriesToPlot ) };

        if ( $( "#isZeroGraph" ).is( ':checked' ) )
            yAxisSettings = { min: 0 };

        // http://www.jqplot.com/docs/files/jqPlotOptions-txt.html

        $.jqplot( "metricPlot", seriesToPlot, {
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
                    size: 4,
                    color: "black"
                }
            },
            cursor: {
                show: true,
                tooltipLocation: 'nw',
                zoom: true
            },
            axes: {
                xaxis: {
                    // http://www.jqplot.com/tests/date-axes.php
                    renderer: $.jqplot.DateAxisRenderer,
                    tickOptions: { formatString: '%b %#d' }
                },
                yaxis: yAxisSettings

            },
            // http://www.jqplot.com/docs/files/plugins/jqplot-highlighter-js.html
            highlighter: {
                show: true,
                showMarker: true,
                tooltipLocation: "ne",
                sizeAdjust: 20,
                formatString: "%s : %s",
                tooltipContentEditor: function ( str, seriesIndex, pointIndex, plot ) {
                    //the str is the ready string from tooltipFormatString
                    //depending on how do you give the series to the chart you can use plot.legend.labels[seriesIndex] or plot.series[seriesIndex].label
                    return '<b><span class=tip-legend>' + plot.legend.labels[ seriesIndex ] + ': </span></b>' + str;
                },
            },
            legend: {
                labels: seriesLabel,
                placement: "outside",
                show: true
            }
        } );

        $( '#metricPlot' ).on( 'jqplotClick',
            function ( ev, seriesIndex, pointIndex, data ) {
                if ( isDisplayZoomHelpOnce ) {
                    isDisplayZoomHelpOnce = false;
                    alertify.notify( "double click to reset zoom to original" );
                }
            }
        );

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


    function buildSeries( xArray, yArray ) {
        let graphPoints = new Array();

        for ( let i = 0; i < xArray.length; i++ ) {
            let resourcePoint = new Array();
            resourcePoint.push( xArray[ i ] );

            let metricVal = yArray[ i ];
            if ( $( "#nomalizeContainer" ).is( ":visible" ) ) {
                metricVal = metricVal * $( "#nomalizeContainer select" ).val();
            }

            resourcePoint.push( metricVal );

            graphPoints.push( resourcePoint );
        }

        xArray = null;
        yArray = null;
        // console.log( "Points: " + JSON.stringify(graphPoints ) );

        return graphPoints;
    }

}