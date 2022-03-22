// define( [ "./graphLayout", "mathjs" ], function ( graphLayout, mathjs ) {

//     console.log( "Module loaded: graphPackage/flotUtils" ) ;


import _dom from "../utils/dom-utils.js";
import graphLayout from "./graphLayout.js"


import _csapCommon from "../utils/csap-common.js" ;

const hostGraphs = graphs_host();

export default hostGraphs

function graphs_host() {

    _dom.logHead( "Module loaded2" );


    let hackLengthForTesting = -1;

    let bindTimer, tipTimer;


    let STATS_FORMAT = { notation: 'fixed', precision: 2 };


    return {
        //
        buildHostStatsPanel: function ( $statsPanel, graphTitle ) {
            return buildHostStatsPanel( $statsPanel, graphTitle );
        },
        //
        buildPlotPanel: function ( title, resourceGraph, numGraphs, host, graphName, checkId, statsBuilder, titleTip ) {
            return buildPlotPanel( title, resourceGraph, numGraphs, host, graphName, checkId, statsBuilder, titleTip )
        },
        //
        getPlotOptionsAndXaxis: function ( $plotPanel, graphName, flotTimeOffsetArray, linesOnGraphArray, $GRAPH_INSTANCE, sampleInterval, isSampling, isMultiHost ) {
            return getPlotOptionsAndXaxis( $plotPanel, graphName, flotTimeOffsetArray, linesOnGraphArray, $GRAPH_INSTANCE, sampleInterval, isSampling, isMultiHost );
        },
        //
        addPlotAndLegend: function ( resourceGraph, numGraphs, graphName, host, plotPanel, plotContainer ) {
            return addPlotAndLegend( resourceGraph, numGraphs, graphName, host, plotPanel, plotContainer )
        },
        //
        configurePanelEvents: function ( $targetPanel, $panelContainer, graphsArray ) {
            configurePanelEvents( $targetPanel, $panelContainer, graphsArray )
        }
    }

    /**
     * http://www.flotcharts.org/, http://flot.googlecode.com/svn/trunk/API.txt
     * 
     * @param newHostContainerJQ
     * @param graphMap
     */

    function buildPlotPanel( title, resourceGraph, numGraphs, host, graphName, checkId, statsBuilder, titleTip ) {

        // console.log( "title: " + title );

        let $graphContainer = resourceGraph.getCurrentGraph();

        let $plotPanel = jQuery( '<div/>', {
            id: host + "_plot_" + graphName,
            class: "plotPanel " + graphName,
            "data-graphname": graphName
        } );

        let titleHoverText = "Click and drag graph title to re order";
        if ( titleTip ) {
            titleHoverText = titleTip + "\n" + titleHoverText;
        }
        let $graphTitleBar = jQuery( '<div/>', {
            id: "Plot" + host + graphName,
            title: titleHoverText,
            class: "graphTitle"
        } );

        jQuery( '<span/>', {
            html: title,
            class: "name"
        } ).appendTo( $graphTitleBar );



        $plotPanel.append( $graphTitleBar );

        jQuery( '<label/>', {
            text: "",
            class: "graphNotes"
        } ).appendTo( $graphTitleBar );


        let $panelControls = jQuery( '<span/>', {
            text: "",
            class: "panel-controls"
        } ).appendTo( $graphTitleBar );

        jQuery( '<button/>', {
            class: "plotAboutButton csap-icon csap-info-black",
            title: "View Summary Information about data points"
        } )
            .click( function () {
                console.log( `plotAboutButton clicked` );

                let content = "";
                for ( let i = 0; i < statsBuilder.lines.length; i++ ) {
                    content += statsBuilder.function( statsBuilder.lines[ i ], statsBuilder.title[ i ] );
                }
                // console.log("$aboutButton: " + content);
                $( "#graphToolTip" ).hide();
                alertify.alert( "Summary Statistics: " + title, content );
            } )
            .data( "graphType", checkId )
            .appendTo( $panelControls );

        jQuery( '<button/>', {
            class: "plotMinMaxButton csap-icon csap-window",
            title: "clock to toggle size"
        } )
            .click( function () {
                console.log( `minMaxButton clicked` );

                if ( !graphLayout.isAGraphMaximized( $graphContainer ) ) {
                    $( ".graphCheckboxes input.graphs", $graphContainer ).prop(
                        "checked", false );
                    $( "#" + $( this ).data( "graphType" ), ".graphCheckboxes" ).prop( "checked",
                        true );
                } else {

                    $( ".graphCheckboxes input.graphs", $graphContainer ).prop(
                        "checked", true );
                }
                resourceGraph.reDraw();
            } )
            .data( "graphType", checkId )
            .appendTo( $panelControls );

        return $plotPanel;
    }

    function buildHostStatsPanel( linesOnGraphArray, graphTitle ) {
        console.log( `buildHostStatsPanel() graphTitle: ${ graphTitle } numLines: ${ linesOnGraphArray.length }` );

        let $statsPanel = jQuery( '<div/>', { class: "statsPanel " } );
        for ( let graphDefinition of linesOnGraphArray ) {
            // if ( i == (linesOnGraphArray.length-1))  console.log( JSON.stringify( graphDefinition, null, "\t" ) )
            let $seriesContainer = jQuery( '<div/>', { class: "statsContainer" } );
            $seriesContainer.append( jQuery( '<div/>', { class: "statsLabel", html: graphDefinition.label } ) );



            let stats = graphStats( graphDefinition.rawData );

            if ( !stats ) {
                continue;
            }

            addStatsItems( graphTitle, $seriesContainer, stats.all, "All" );
            addStatsItems( graphTitle, $seriesContainer, stats.nonZero, "values > 0" );

            //$statsLabel.append ( JSON.stringify( graphDefinition.stats, null, "<br/>" ) );
            $statsPanel.append( $seriesContainer );
        }
        return $statsPanel.html();
    }

    function addStatsItems( graphTitle, $seriesContainer, samples, categoryTitle ) {

        if ( !samples || !samples.Collected ) {
            return;
        }
        $seriesContainer.append( jQuery( '<div/>', { class: "statsType", text: categoryTitle + " : " } ) );

        for ( let type in samples ) {
            // console.log( "type: " + type );

            let $itemLabel = jQuery( '<div/>', { text: type + " : " } );
            let rawVal = samples[ type ];
            let suffix = "";
            if ( type != "Collected" && graphTitle.contains( "ms" ) && rawVal > 2000 ) {
                rawVal = ( rawVal / 1000 ).toFixed( 1 );

                if ( rawVal > 60 ) {
                    rawVal = Math.round( rawVal );
                }
                suffix = '<span class="statsUnits">s</span>'
            }

            let val = numberWithCommas( rawVal );
            let theStyles = "";
            if ( type.contains( "2x" ) )
                theStyles = "red";
            $itemLabel.append( jQuery( '<span/>', { class: theStyles, html: val + suffix } ) )
            $seriesContainer.append( $itemLabel );
        }

    }


    function graphStats( graphData ) {

        //console.log( "graphStats(): " + JSON.stringify( graphData ) );

        let allStats = new Object();

        try {

            allStats.all = calculateStats( graphData );

            let non0Values = new Array();
            for ( let i = 0; i < graphData.length; i++ ) {
                if ( graphData[ i ] != 0 ) {
                    non0Values.push( graphData[ i ] )
                }

            }

            let percentZeros = ( graphData.length - non0Values.length ) / graphData.length;

            if ( percentZeros > 0.1 ) {
                // for low hit services eliminate the 0's to avoid weighting the diffs
                allStats.nonZero = calculateStats( non0Values );
            }

        } catch ( err ) {
            console.log( "failed:" + err );
        }

        //console.log( "graphStats(): " + JSON.stringify( allStats ) );

        return allStats;
    }

    function calculateStats( dataArray ) {

        console.log( dataArray );

        let stats = new Object();
        stats[ "Collected" ] = dataArray.length;

        let mean = _csapCommon.calculateAverage( dataArray );
        stats[ "mean" ] = mean.toFixed( 2 );
        stats[ "std. deviation" ] = _csapCommon.calculateStandardDeviation( dataArray ).toFixed( 2 );

        stats[ "samples > 2x mean" ] = countItemsGreaterThan( dataArray, mean, 2 );;
        stats[ "median" ] = _csapCommon.findMedian( dataArray );
        stats[ "min" ] = Math.min( ...dataArray );
        stats[ "max" ] = Math.max( ...dataArray );


        // let mean = getMathJs().mean( dataArray );
        // stats[ "mean" ] = getMathJs().format( mean, STATS_FORMAT );
        // stats[ "std. deviation" ] = getMathJs().format( getMathJs().std( dataArray ), STATS_FORMAT );

        // stats[ "samples > 2x mean" ] = countItemsGreaterThan( dataArray, mean, 2 );
        // stats[ "median" ] = getMathJs().format( getMathJs().median( dataArray ) );
        // stats[ "min" ] = getMathJs().min( dataArray );
        // stats[ "max" ] = getMathJs().max( dataArray );

        return stats;
    }



    function countItemsGreaterThan( dataArray, mean, scale ) {

        let threshhold = mean * scale;
        let numOverThreshold = 0;

        for ( const dataItem of dataArray ) {
            if ( dataItem > threshhold  ) {
                numOverThreshold++;
            }
        }

        return numOverThreshold;
    }

    function numberWithCommas( x ) {
        return x.toString().replace( /\B(?=(\d{3})+(?!\d))/g, "," );
    }

    function getPlotOptionsAndXaxis( $plotPanel, graphName, flotTimeOffsetArray, linesOnGraphArray, $GRAPH_INSTANCE, sampleInterval, isSampling, isMultiHost ) {

        let isStack = !$( '.useLineGraph', $GRAPH_INSTANCE ).prop( "checked" );

        if ( graphName == "OS_Load" )
            isStack = false;


        let isMouseNav = $( '.zoomAndPan', $GRAPH_INSTANCE ).prop( "checked" );

        let mouseSelect = "xy"; // x, y, or xy

        //        if ( isMouseNav )
        //            mouseSelect = "xy" ;

        let plotWidth = Math.floor( $( ".plotPanel ." + graphName, $GRAPH_INSTANCE ).outerWidth( true ) );

        if ( plotWidth < 400 ) {
            console.log( `getPlotOptionsAndXaxis() plotWidth: ${ plotWidth }, updating to 400 minimal` );

            plotWidth = 400;
            $( ".plotPanel ." + graphName, $GRAPH_INSTANCE ).css( "width", plotWidth );
        }

        let numLegendColumns = Math.floor( plotWidth / 140 );
        // console.log( "graphName: " + graphName + " width: "  + plotWidth + " numLegendColumns" + numLegendColumns) ;

        let plotOptions = {
            series: {
                stack: isStack,

                lines: {
                    show: true,
                    fill: isStack
                },
                points: {
                    show: false
                },
            },
            legend: {
                position: "nw",
                show: true,
                noColumns: numLegendColumns,
                container: null
            },
            selection: {
                // "xy"
                mode: mouseSelect
            },
            grid: {
                hoverable: true,
                clickable: false
            },
            yaxis: {
                zoomRange: false,
                showMinorTicks: false,
                axisLabel: 'y-csap-label'
                //                autoScaleMargin: 2
            },
            zoom: {
                interactive: isMouseNav
            },
            pan: {
                interactive: isMouseNav
            },
            xaxis: buildTimeAxis(
                flotTimeOffsetArray,
                sampleInterval,
                $( "#numSamples", $GRAPH_INSTANCE ).val()
                , isSampling, plotWidth )
        };

        if ( isOutsideLegend( $GRAPH_INSTANCE ) ) {
            plotOptions.legend.container = $( ".Legend" + graphName, $GRAPH_INSTANCE )[ 0 ];
        }




        // 
        try {
            scaleLabels( $plotPanel, linesOnGraphArray, plotOptions )
        } catch ( e ) {
            console.log( "Failed to scaleLabel: ", e );
        }


        //for ( let i = graphSeries.data.length - 5; )


        return plotOptions;
    }

    function scaleLabels( $plotPanel, linesOnGraphArray, plotOptions ) {

        let $titleSpan = $( ".graphTitle span.name", $plotPanel );
        let title = $titleSpan.text();
        console.log( `scaleLabels() title: ${ title }` );
        // big assumption - use last value of  first series.
        let graphSeries = linesOnGraphArray[ 0 ];
        // let firstY = (graphSeries.data[ 1 ])[1] ;
        //plotOptions.yaxis.min = firstY ; this defaults in line mode
        let lastPointInSeries = graphSeries.data[ graphSeries.data.length - 1 ];
        let lastY = lastPointInSeries[ 1 ];

        // + " firstY: " + firstY
        //				console.log( "graphName: " + graphName 
        //						   + " last y: " + lastY )

        if ( lastY > 1000 ) {
            console.log( title + " lasty: " + lastY );

            if ( title.toLowerCase().contains( "(mb)" ) ) {
                $titleSpan.text( title.substring( 0, title.length - 4 ) );

                plotOptions.yaxis.tickFormatter =
                    function ( val, axis ) {
                        val = val / 1024;
                        return val.toFixed( 1 ) + "Gb";
                    }
            } else if ( title.toLowerCase().contains( "(kb)" ) ) {

                $titleSpan.text( title.substring( 0, title.length - 4 ) );
                plotOptions.yaxis.tickFormatter =
                    function ( val, axis ) {
                        val = val / 1024;
                        return val.toFixed( 1 ) + "Mb";
                    }
            } else if ( title.toLowerCase().contains( "(ms)" ) ) {

                $titleSpan.text( title.substring( 0, title.length - 4 ) );
                plotOptions.yaxis.tickFormatter =
                    function ( val, axis ) {
                        val = val / 1000;
                        return val.toFixed( 1 ) + "s";
                    }
            } else {
                plotOptions.yaxis.tickFormatter =
                    function ( val, axis ) {
                        val = val / 1000;
                        return val.toFixed( 1 ) + "K";
                    }
            }
        }
        if ( lastY > 1000000 ) {
            // console.log( "OsMpStat: " + jsonToString( graphSeries.data ) + " lasty: " + lastY );
            plotOptions.yaxis.tickFormatter =
                function ( val, axis ) {
                    val = val / 1000000;
                    return val.toFixed( 2 ) + "M";
                }
        }
    }


    /**
     * http://www.flotcharts.org/
     * http://flot.googlecode.com/svn/trunk/API.txt
     * @param newHostContainerJQ
     * @param graphMap
     */

    function buildTimeAxis( flotTimeOffsetArray, sampleIntervalInSeconds, maxWindowSamples, isSampled, plotWidth ) {

        let flotXaxisConfig = {
            mode: "time",
            timeBase: "milliseconds",
            timeformat: "%H:%M<br>%b %d",
            showMinorTicks: false,
            axisLabel: 'x-csap-label'
        };

        //		  console.log( "buildTimeAxis() time array size: " + flotTimeOffsetArray.length + " isSampled:" + isSampled
        //					 + " plotWidth: " + plotWidth);
        let numItems = flotTimeOffsetArray.length - 1;

        //maxWindowSamples = 10;
        let samplesOnGraph = maxWindowSamples;
        if ( maxWindowSamples > numItems ) {
            samplesOnGraph = numItems;
        }


        console.log( `buildTimeAxis: maxWindowSamples: ${ maxWindowSamples },  display points: ${ samplesOnGraph } ` );

        // handle time scrolling
        if ( !isSampled ) {

            flotXaxisConfig.autoScale = "none" // "none" or "loose" or "exact" or "sliding-window"
            flotXaxisConfig.min = flotTimeOffsetArray[ samplesOnGraph ];
            flotXaxisConfig.max = flotTimeOffsetArray[ 0 ];
            // flotXaxisConfig.minTickSize =  [1, "minute"] ;
            //            flotXaxisConfig.panRange = [
            //                flotTimeOffsetArray[0], 
            //                flotXaxisConfig.max
            //            ] ;
            //            flotXaxisConfig.panRange = false ;

            // flotXaxisConfig.zoomRange = [graphMap.usrArray[numItems][0] ,
            // graphMap.usrArray[0][0] ] ;

        }
        if ( plotWidth < 270 ) {
            flotXaxisConfig.ticks = 3;
        }

        return flotXaxisConfig;
    }



    function addPlotAndLegend( resourceGraph, numGraphs, graphName, host, $plotFullPanel, $plotContainer ) {

        console.log( `adding: ${ graphName } on host ${ host } to ${ $plotFullPanel.attr( "id" ) }` );

        let $graphContainer = resourceGraph.getCurrentGraph();


        let $graphPlot = jQuery( '<div/>', {
            id: "Plot" + host + graphName,
            class: "graphPlot " + graphName
        } ).appendTo( $plotFullPanel );

        // Support panel resizing
        $plotFullPanel.resizable( {
            stop: function ( event, ui ) {
                // console.log("width: " + ui.size.width + " height: " + ui.size.height) ;
                graphLayout.setSize( graphName, ui.size, $graphContainer, $plotContainer );
                resourceGraph.reDraw();
            },
            start: function ( event, ui ) {
                $( "div.graphPlot", $plotFullPanel ).remove();
            }
        } );
        $plotFullPanel.on( 'resize', function ( e ) {
            // otherwise resize window will be continuosly called
            e.stopPropagation();
        } );

        $plotContainer.append( $plotFullPanel );
        $graphPlot.css( "height", "100%" );
        $graphPlot.css( "width", "100%" );


        let containerOffset = 20;

        if ( $( ".hostContainer", $graphContainer.parent() ).length > 0
            && $( ".graphOptions", $graphContainer.parent().length > 0 ) ) {
            containerOffset = $( ".hostContainer", $graphContainer.parent() ).offset().top
                + $( ".graphOptions", $graphContainer.parent() ).offset().top
                + 20;
        } else {
            console.warn( ` failed to resolve ".hostContainer", $graphContainer.parent()  ` );
        }
        let fullHeight = $( window ).outerHeight( true )
            - containerOffset;

        console.log( `addPlotAndLegend() containerOffset: ${ containerOffset } fullHeight: ${ fullHeight } ` );
        let targetHeight = fullHeight;

        if ( graphLayout.getHeight( graphName, $graphContainer ) != null ) {
            targetHeight = graphLayout.getHeight( graphName, $graphContainer );
            //console.log("addPlotAndLegend() targetHeight: " + targetHeight  + " type: " + (typeof targetHeight) )   ;

            if ( typeof targetHeight == "string" && targetHeight.contains( "%" ) ) {
                let percent = targetHeight.substring( 0, targetHeight.length - 1 );
                targetHeight = Math.floor( fullHeight * percent / 100 );
            }

            if ( targetHeight < 150 )
                targetHeight = 150;
        }
        // Support for nesting on other pages
        if ( window.csapGraphSettings != undefined ) {
            targetHeight = window.csapGraphSettings.height;
            console.log( "getCsapGraphHeight: " + targetHeight );
        }



        //		  
        // plotDiv.css( "height", targetHeight ); // height is applied to plot div
        //plotPanel.css( "height", targetHeight ) ;
        $graphPlot.css( "height", targetHeight - 50 );

        if ( $( ".includeFullLegend", $graphContainer ).is( ":checked" ) ) {
            //plotPanel.css( "height", "auto" ) ;
            //plotDiv.css( "height", targetHeight ) ;
        }


        let fullWidth = $( window ).outerWidth( true ) - 100;
        let $newNav = $( "article.navigation" );
        if ( $newNav.length > 0 ) {
            let oldWidth = fullWidth;
            fullWidth = fullWidth - $newNav.outerWidth( true ) - 10;
            console.log( `new nav detected, max width updated: from ${ oldWidth } to ${ fullWidth }` );
        }

        let targetWidth = fullWidth;

        if ( graphLayout.getWidth( graphName, $graphContainer ) != null ) {
            targetWidth = graphLayout.getWidth( graphName, $graphContainer );
            if ( typeof targetWidth == "string" && targetWidth.contains( "%" ) ) {
                let percent = targetWidth.substring( 0, targetWidth.length - 1 );
                targetWidth = Math.floor( fullWidth * percent / 100 );
            }

            if ( targetWidth < 400 )
                targetWidth = 400;
        }


        // Support for nesting on other pages
        if ( window.csapGraphSettings != undefined ) {
            targetWidth = window.csapGraphSettings.width;
            console.log( "getCsapGraphWidth: " + targetWidth );
        }
        // console.log( "targetWidth: " + targetWidth + " height: " + targetHeight );

        $plotFullPanel.css( "width", targetWidth ); // width is applied to entire panel

        let numHosts = $( ".instanceCheck:checked" ).length;
        // console.log(" num Hosts Checked" + numHosts) ;
        let isMultipleHosts = false;
        isMultipleHosts = numHosts > 1; // template is one, plus host is
        // another

        let useAutoSelect = $( ".useAutoSize", $graphContainer ).is(
            ':checked' );


        if ( useAutoSelect && numGraphs == 1 && !isMultipleHosts ) {
            $graphPlot.css( "height", "600px" );
        }


        if ( !isOutsideLegend( $graphContainer ) ) {
            // console.log("Need to add title") ;
        } else {
            //return plotDiv;
            let $graphBottomPanel = jQuery( '<div/>', {
                class: "graphBottomPanel"
            } ).appendTo( $plotFullPanel );

            let $plotLegendContainer = jQuery( '<div/>', {
                class: "legend Legend" + graphName
            } ).appendTo( $graphBottomPanel );
            //plotLegendDiv.css( "max-height", "50px" ) ;

            if ( targetWidth <= 300 ) {
                $plotLegendContainer.addClass( "legendOnHover" );
                //plotLegendDiv.hide();
                $( ".graphNotes, .titleHelp", $plotFullPanel ).hide();
            } else {

                $plotLegendContainer.show();
                $( ".graphNotes, .titleHelp", $plotFullPanel ).show();
            }

        }


        return $graphPlot;
    }




    function isOutsideLegend( $graphContainer ) {

        if ( graphLayout.isAGraphMaximized( $graphContainer ) )
            return false;
        return $( '.outsideLabels', $graphContainer ).prop( "checked" );
    }


    function configurePanelEvents( $targetPanel, $panelContainer, graphsArray ) {

        configureToolTip( $targetPanel, $panelContainer );
        configurePanelZooming( $targetPanel, $panelContainer, graphsArray )

    }

    function configureToolTip( $targetPanel, $panelContainer ) {

        let hideWithTimeout = function () {
            tipTimer = setTimeout( function () {
                $( "#graphToolTip", $panelContainer ).hide();
            }, 500 )
        }

        $targetPanel.bind( "plothover", function ( event, pos, item ) {

            //  $( "#graphToolTip" ).hide();
            if ( item === null ) {
                clearTimeout( tipTimer );
                clearTimeout( bindTimer );
                hideWithTimeout();
                return;
            }
            // console.log( "item" + jsonToString( item ) );


            clearTimeout( tipTimer );
            clearTimeout( bindTimer );
            bindTimer = setTimeout( function () {
                let xValue = new Date( item.datapoint[ 0 ] ), yValue = item.datapoint[ 1 ]
                    .toFixed( 2 );

                xValue.addMinutes( xValue.getTimezoneOffset() );

                let formatedDate = xValue.format( "HH:MM mmm d" );

                let label = item.series.label;

                if ( label == null )
                    return;
                // support for mbean attributes versus entire location
                if ( label.indexOf( ':' ) != -1 )
                    label = label.substring( 0, label.indexOf( ':' ) );
                if ( label.indexOf( '<' ) != -1 )
                    label = label.substring( 0, label.indexOf( '<' ) );

                let tipContent = '<div class="tipInfo">' + label + " <br>" + formatedDate
                    + "</div><div class='tipValue'>"
                    + numberWithCommas( yValue ) + "</div>";

                let offsetY = 100;

                if ( window.csapGraphSettings != undefined ) {
                    offsetY = 150;
                }

                let offsetX = item.pageX + 15;

                if ( $( "body#manager" ).length > 0 ) {

                    //
                    //  Application Browser
                    //


                    if ( $( "#panelControls" ).length > 0
                        && $( "#panelControls" ).is( ":visible" ) ) {
                        let panelOffset = $( "#panelControls" ).parent().css( "left" );
                        offsetX = offsetX - parseInt( panelOffset ) - 120;
                        offsetY = 160;
                    } else {
                        offsetY = 100;
                        offsetX = item.pageX + 15;
                    }
                } else if ( $( "body#analytics-portal" ).length > 0 ) {
                    offsetY = 180;
                    offsetX = item.pageX - 50;
                }

                console.log( `configureToolTip: offset ${ offsetY }`, item, ` position: `, pos, ` event ${ event.currentTarget.clientLeft }...`, event );
                $( "#graphToolTip", $panelContainer ).html( tipContent )
                    .css( {
                        top: item.pageY - offsetY,
                        left: offsetX
                    } ).fadeIn( 200 );
            }, 500 );



        } );
    }

    function configurePanelZooming( $targetPanel, $panelContainer, graphsArray ) {

        $targetPanel.bind( "plotselected", function ( event, ranges ) {

            // console.log("Got " + $(this).attr("id") ) ;
            let bindHost = $( this ).parent().parent().data(
                "host" );
            console.log( `configurePanelZooming() plot selected: ${ bindHost }`, ranges );

            let hostPlots = graphsArray[ bindHost ];

            let hostOriginalZoom = new Array();
            for ( let i = 0; i < hostPlots.length; i++ ) {

                let currentPlotOptions = hostPlots[ i ].getOptions();

                let yaxis = hostPlots[ i ].getAxes().yaxis;
                let yaxisOptions = currentPlotOptions.yaxes[ 0 ];

                if ( $( ".resetInterZoom", $panelContainer ).length == 0 ) {
                    //console.log( `axes: `, currentPlotOptions.xaxes[0], yaxisOptions.axisLabel, yaxis ) ;
                    let saved = new Object();
                    hostOriginalZoom.push( saved );
                    // preserver orig
                    saved.xmin = currentPlotOptions.xaxes[ 0 ].min;
                    saved.xmax = currentPlotOptions.xaxes[ 0 ].max;

                    //console.log( `${ yaxisOptions.axisLabel } yfrom: ${ yaxis.datamin } `  ) ;

                    saved.ymin = yaxis.datamin;
                    saved.ymax = yaxis.datamax;
                }

                if ( hostPlots[ i ].getSelection() != null ) {
                    // only Zoom Y on current graph
                    currentPlotOptions.yaxes[ 0 ].min = ranges.yaxis.from;
                    currentPlotOptions.yaxes[ 0 ].max = ranges.yaxis.to;
                }


                currentPlotOptions.xaxes[ 0 ].min = ranges.xaxis.from;
                currentPlotOptions.xaxes[ 0 ].max = ranges.xaxis.to;

                hostPlots[ i ].setupGrid();
                hostPlots[ i ].draw();
                hostPlots[ i ].clearSelection();
            }

            console.log( `hostOriginalZoom settings: `, hostOriginalZoom );

            if ( $( ".resetInterZoom", $panelContainer ).length == 0 ) {

                // console.log("Creating reset") ;
                let $resetPlotButton = jQuery( '<button/>', {
                    class: `csap-icon csap-remove resetInterZoom`,
                    title: `Click to restore graph view to default`,
                    text: "Reset View"
                } );
                $( "span.reset-graph-button", $targetPanel.parent().parent() ).append( $resetPlotButton );

                $resetPlotButton.click( function () {

                    console.log( `configurePanelZooming() $resetPlotButton clicked, graph count count: ${ hostPlots.length }` );

                    $( this ).remove();
                    for ( let i = 0; i < hostPlots.length; i++ ) {
                        let targetPlot = hostPlots[ i ];

                        // latest plot requires numbers and precision set correctly on xmin

                        //targetPlot.clearSelection() ;

                        //                        console.log( `updating x min ${targetPlot.getOptions().xaxes[0].min} to ${hostOriginalZoom[i].xmin}`
                        //                            + ` type: ${ typeof hostOriginalZoom[i].xmin }`,
                        //                                targetPlot.getOptions().xaxes[0] ) ;

                        //targetPlot.getOptions().xaxes[0].min = hostOriginalZoom[i].xmin ;
                        targetPlot.getOptions().xaxes[ 0 ].min = Number( hostOriginalZoom[ i ].xmin + ".0000" );
                        //targetPlot.getOptions().xaxes[0].max = hostOriginalZoom[i].xmax ;
                        targetPlot.getOptions().xaxes[ 0 ].max = Number( hostOriginalZoom[ i ].xmax + ".0000" );

                        targetPlot
                            .getOptions().yaxes[ 0 ].min = hostOriginalZoom[ i ].ymin;
                        targetPlot
                            .getOptions().yaxes[ 0 ].max = hostOriginalZoom[ i ].ymax;

                        targetPlot.setupGrid();
                        targetPlot.draw();


                    }
                } );


            }

        } );
    }
}
