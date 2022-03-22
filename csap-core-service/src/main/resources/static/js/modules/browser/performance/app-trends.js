define( [ "browser/utils" ], function ( utils ) {
    console.log( "Module loaded" ) ;
    let trendDefinitions = null ;
    let $trendDefinitionsLoaded = new $.Deferred() ;

    let $contentLoaded = null ;


    // Hooks for not reIssuing null requests
// Critical for trending graphs which require explicity tool tip
// layout based on container index
    let nullTrendItems = new Array() ;
    let _activePlots = new Array() ;
    let trendTimer = 0 ;
    const TREND_CHECK_FOR_DATA_SECONDS = 4 ;

    let _linkProject = null ;
    let _project = null ;
    let isDisplayZoomHelpOnce = true ;

    let $container = null ;
    let $slide = null ;
    let numberOfDays ;
    let showHosts = false ;
    let slideShowMode = false ;
    let $applicationSlideShow ;

    let slideIndex = "trend-0" ;

    let printRenderingOnce = true ;
    let debug = false ;

    return {
        init: function ( isUseLocalRefresh ) {

            init( isUseLocalRefresh ) ;
        },

        reDraw: function ( ) {
            if ( $container && $container.is( ":visible" ) ) {
                getConfiguredTrends() ;
            }
        },

        show: function ( $theContainer, theNumberOfDays, byHost, $applicationSlideShowCheck ) {

            $container = $theContainer ;
            $slide = $( "#slide-show", $container.parent() ) ;
            numberOfDays = theNumberOfDays ;
            showHosts = byHost ;
            $applicationSlideShow = $applicationSlideShowCheck ;
            slideShowMode = $applicationSlideShowCheck.is( ":checked" ) ;

            _project = utils.getActiveProject() ;
            _linkProject = utils.getActiveProject( false ) ;


            $contentLoaded = new $.Deferred() ;

            // spin on another thread to get groupCollapsed logs in sync
            $.when( $trendDefinitionsLoaded ).done( getConfiguredTrends ) ;
            //setTimeout( () => getConfiguredTrends(), 100 ) ;
            return $contentLoaded ;
        }

    } ;

    function init( isUseLocalRefresh ) {

//        $( '#trendingDays' ).selectmenu( {
//            change: function ( event, ui ) {
//                // force full screen every time
//                if ( isUseLocalRefresh ) {
//                    $container.addClass( "panelEnlarged" ) ;
//                    getConfiguredTrends() ;
//                } else {
//                    $( "#trendPanel" ).removeClass( 'panelEnlarged' ) ;
//                    $( "#minMaxTrendsButton" ).trigger( "click" ) ;
//                }
//            }
//        } ) ;

        getTrendDefinition() ;

    }

    function getTrendDefinition() {
        $.getJSON(
                APP_BROWSER_URL + "/trend/definition" )
                .done( function ( trendDefinitionResponse ) {

                    trendDefinitions = trendDefinitionResponse ;
                    $trendDefinitionsLoaded.resolve() ;

                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    handleConnectionError( "clearing alerts", errorThrown ) ;
                } ) ;
    }

    function getConfiguredTrends() {
        console.log( `getConfiguredTrends() building: ${trendDefinitions.length} trends` ) ;


        console.log( `getConfiguredTrends() purging previous plots: ${_activePlots.length}` ) ;
        for ( let plotInstance of _activePlots ) {

            //console.log( "deleteing plot " ) ;
            plotInstance.destroy() ;
            delete plotInstance ;

        }

        // trendDefinition
        // data is incremental now....

        $( "table.jqplot-table-legend", $slide ).remove() ;
        buildTrendingGraph( "slide", null ) ;


        let containerIndex = 0 ;
        let trendCount = 0 ;
        for ( let trendDefinition of  trendDefinitions ) {

//            if ( trendCount++ > 0 ) break ; // testing
            // skip some items....
            if ( $.inArray( trendDefinition.label, nullTrendItems ) == -1 ) {
                buildTrendingGraph( containerIndex, trendDefinition ) ;
                containerIndex++ ;
            }
        }

        // Some graphs are removed as there is no data - clean those up
        for ( let i = 0 ; i < nullTrendItems.length ; i++ ) {
            $( "#trend" + ( trendDefinitions.length - i - 1 ) )
                    .empty()
                    .text( "No Data for: " + nullTrendItems[i] )
                    .hide() ;
        }


    }


    function buildTrendingGraph( index, trendDefinition ) {

        let containerId = "trend-" + index ;

        if ( $( "#" + containerId ).length == 0 ) {

            let trendClasses = "trendGraph" ;
            let $trendParent = $container ;
            if ( index === "slide" ) {
                trendClasses = "trendGraph trend-slide" ;
                $trendParent = $( ".the-plot", $slide ) ;
            }

            let $panel = jQuery( '<div/>', {
                class: trendClasses
            } ) ;

            jQuery( '<div/>', {
                id: containerId
            } ).appendTo( $panel ) ;

            $trendParent.append( $panel ) ;
        }

        if ( index === "slide" ) {
            renderTrendingGraph( containerId ) ;
            return ;
        }

        //return;


        let paramObject = {
            report: trendDefinition.report,
            metricsId: trendDefinition.metric,
            appId: utils.getAppId(),
            project: utils.getActiveProject( false ),
            life: utils.getActiveEnvironment(),
            trending: 1,
            trendDivide: trendDefinition.divideBy,
            perVm: showHosts,
            numDays: numberOfDays
        } ;

        if ( trendDefinition.serviceName != undefined ) {
            $.extend( paramObject, {
                serviceName: trendDefinition.serviceName
            } ) ;
        }

        if ( trendDefinition.allVmTotal != undefined ) {
            $.extend( paramObject, {
                allVmTotal: trendDefinition.allVmTotal
            } ) ;
        }

        // console.log("Getting Trending data for: " + trendItem.report) ;
        $.getJSON( utils.getTrendUrl(),
                paramObject )

                .done( function ( responseJson ) {
                    renderTrendingGraph( containerId, trendDefinition, responseJson ) ;

                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    handleConnectionError( "Trending request: " + trendDefinition.metric, errorThrown ) ;

                } ) ;


    }


    function renderTrendingGraph( containerId, trendDefinition, trendData ) {


        let labelArray = new Array() ;
        let $trendContainer = $( "#" + containerId, $container ) ;
        let $activeSlide = $( "#trend-slide", $slide ) ;

        let graphHeight = "15em" ;
        if ( slideShowMode ) {
            graphHeight = "10em" ;
        }
        $trendContainer.css( "height", graphHeight ) ;
        if ( containerId === "trend-slide" ) {
            
            if ( slideShowMode ) {
                //grid-template-columns: repeat(auto-fit, 30em);
                $container.addClass( "grid" ) ;
                let slideHeight = Math.round($(window).outerHeight() / 2 ) ;
                $activeSlide.css( "height", slideHeight) ;
            } else {
                $container.removeClass( "grid" ) ;
            }
        }

        // no data available yet
        if ( !trendData || ( trendData.lastUpdateMs === 0 ) ) {
            if ( containerId === "trend-slide" ) {
                if ( slideShowMode ) {
                    showTrendLoading( $activeSlide, "Slide Mode" ) ;
                } else {
                    $slide.hide() ;
                }
                return ;
            }
            showTrendLoading( $trendContainer, trendDefinition.label ) ;
            resetTrendTimer( TREND_CHECK_FOR_DATA_SECONDS ) ;
            return ;
        }

        if ( printRenderingOnce && !debug ) {
            printRenderingOnce = false ;
        }

        $contentLoaded.resolve() ;
        //return ;

        // data available - but stale. Update display, then refresh.
        if ( trendData.lastUpdateMs < 0 ) {
            resetTrendTimer( TREND_CHECK_FOR_DATA_SECONDS )
        }

        if ( trendData.message != undefined ) {
            console.log( "Undefined trending results for:" + trendDefinition.label + " message:" + trendData.message ) ;
            return ;
        }

        // for testing revised layouts || trendItem.label == "Cores Active" currentResults.data == undefined
        let trendingData = trendData.data ;
        // console.log( "trendingData: ", trendingData );
        if ( ( trendingData === undefined )
                || trendingData[0] === undefined
                || trendingData[0]["date"] === undefined ) {
            console.log( `Hiding ${trendDefinition.label}: no data found, verify definition` ) ;
            $trendContainer.parent().hide() ;
            //resetTrendTimer( TREND_CHECK_FOR_DATA_SECONDS ) ;
            return ;
        }

        $trendContainer.empty() ; // get rid of previous rendering

        // graph libraries do not work unless rendering container is visible
        $trendContainer.parent().show() ;

        // let seriesToPlot = [  currData[  selectedColumn]  ];
        let seriesToPlot = new Array() ;
        let seriesLabel = new Array() ;


        let graphTitle = "" ;
        let timeFormat = '%b %#d' ; // month day
        let showMarker = true ;
        let sortedReports = ( trendData.data ).sort( ( a, b ) => ( a.host > b.host ) ? 1 : -1 ) ;

        for ( let intervalReport of sortedReports ) {

            let metricName = trendDefinition.metric ;
            graphTitle = trendDefinition.label ;
            if ( intervalReport[ metricName ] == undefined ) {
                metricName = "StubData" ;
                graphTitle += "(Test Data)"
                console.log( "using stub data" )
            }

            let timePeriods = intervalReport[ "date"] ;
            if ( intervalReport[  "timeStamp"] ) {
                timePeriods = intervalReport[  "timeStamp"] ;
                timeFormat = '%H:%M'  // {formatString:'%H:%M:%S'} 
            }
            if ( timePeriods.length > 20 ) {
                showMarker = false ;
            }

            seriesToPlot.push( buildSeries( timePeriods, intervalReport[ metricName ] ) ) ;
            console.log( `seriesToPlot: `, seriesToPlot )

            // let label = trendItem.label;
            let label = intervalReport.project ;
            if ( intervalReport["host"] != undefined ) {
                //curLabel += ":" + currData["host"] ;
                label = intervalReport["host"] ;
            }
            seriesLabel.push( label ) ;
//		  if ( currentResults.data.length > 1 )
//				label += " : " + currData.project + " "; // currData.lifecycle
            if ( has10kValue( intervalReport[  metricName] ) )
                graphTitle += " (1000's)" ;

            if ( trendData.lastUpdateMs < 0 ) {
                graphTitle += '<img width="14" src="' + contextUrl + 'images/animated/loadSmall.gif"/>' ;
            }
        }


        //let height = 10 + currentResults.data.length;  // handle legend heights
        // $trendContainer.css( "height", height + "em" );
        // let seriesToPlot = [  buildSeries( currData[  "date"], currData[  selectedColumn])  ];
        // $.jqplot("metricPlot", [[itemValueToday], [itemValueWeek]], {

        // console.log ("Checking for padding: " + graphTitle) ;
        CsapCommon.padMissingPointsInArray( seriesToPlot ) ;

        // console.log( "seriesToPlot:" + JSON.stringify( seriesToPlot, null, "\t" ) );
        // http://www.jqplot.com/docs/files/jqPlotOptions-txt.html

        let toolTipLocation = "ne" ;
        let containerIndex = containerId.substring( 5 ) ;
        if ( containerIndex % 2 == 1 ) {
            toolTipLocation = "nw" ;
        }
        if ( $container.hasClass( "panelEnlarged" ) ) {
            toolTipLocation = "n" ;
        }

        let trendConfiguration = buildTrendConfiguration( showMarker, timeFormat ) ;
        trendConfiguration.title = `<span>${graphTitle}</span>` ;

        if ( numberOfDays > 1 ) {
            trendConfiguration.axes.xaxis.min = new Date( seriesToPlot[0][0][0] ) ;
            trendConfiguration.axes.xaxis.max = new Date() ;
        } else {
            trendConfiguration.axes.xaxis.max = new Date( seriesToPlot[0][0][0] ) ;
        }
        //
        trendConfiguration.highlighter.tooltipLocation = toolTipLocation ;
        trendConfiguration.legend.labels = seriesLabel ;
        //trendConfiguration.legend.placement = "insideGrid" ;



        trendConfiguration.axes.yaxis = { min: calculateGraphMin( seriesToPlot ) } ;

        let thePlot = $.jqplot( containerId, seriesToPlot, trendConfiguration ) ;
        _activePlots.push( thePlot ) ;

        if ( slideShowMode &&
                ( containerId === slideIndex ) ) {
            $slide.show() ;
            $activeSlide.empty() ;
            trendConfiguration.legend.show = true ;
            thePlot = $.jqplot( "trend-slide", seriesToPlot, trendConfiguration ) ;
            _activePlots.push( thePlot ) ;
            $( "table.jqplot-table-legend", $activeSlide )
                    .css( "position", "relative" )
                    .css( "left", "0" )
                    .css( "top", "0" )
                    .css( "height", "1em" )
                    .appendTo( $activeSlide.parent().parent() ) ;
        }

        let $jqPlotTitle = $( ".jqplot-title", $trendContainer ) ;
        $jqPlotTitle
                .css( "position", "relative" )
                .css( "top", "-6px" ) ;

        $jqPlotTitle.off().click( function () {
            slideIndex = $( this ).parent().attr( "id" ) ;
            $applicationSlideShow
                    .prop( "checked", true )
                    .trigger( "change" ) ;
        } ) ;
        // console.log( "generateTrending(): ploting series count: " + seriesToPlot.length );

        if ( trendData.data.length > 1 ) {
            // jqplotDataMouseOver for lines, jqplotDataHighlight for fills
            $( '#' + containerId ).bind( 'jqplotDataMouseOver', function ( ev, seriesIndex, pointIndex, data ) {
                //console.log("binding serires");
                let seriesHighted = trendData.data[seriesIndex]
                trendHighlight( seriesHighted.project )
            } ) ;
        } else {
            $( '#' + containerId ).bind( 'jqplotDataMouseOver', function ( ev, seriesIndex, pointIndex, data ) {
                trendHighlight( trendDefinition.label )
            } ) ;
        }

        registerTrendEvents( containerId, $trendContainer, trendDefinition ) ;
    }

    function showTrendLoading( $trendContainer, label ) {
        if ( printRenderingOnce ) {
            console.log( "renderTrendingGraph() no data available, scheduling a refresh" ) ;
        }
        $trendContainer.parent().show() ;
        let $loadingMessage = jQuery( '<div/>', {
            class: "loadingPanel loading-message",
            html: "loading: <br>" + label
        } ) ;

        // vmid is old
        let $refreshDiv = jQuery( '<div/>', {
            class: "centered"
        } ).append( $loadingMessage ) ;
        $trendContainer.html( $refreshDiv ) ;
    }

    function resetTrendTimer( delaySeconds ) {

        if ( printRenderingOnce ) {
            console.log( "resetTrendTimer() - resetting timer: " + delaySeconds + " seconds" ) ;
        }
        clearTimeout( trendTimer ) ;
        trendTimer = setTimeout( function () {
            if ( $container.is( ":visible" ) ) {
                console.log( "\n\n\n resetTrendTimer() - refreshingTrends\n\n\n" ) ;
                getConfiguredTrends() ;

            }
        }, delaySeconds * 1000 ) ;
    }


    function registerTrendEvents( containerId, $trendContainer, trendItem ) {

        let trendEventsFunction = function () {
            //console.log( "rebinding events - because cursor zooms will loose them" );
            let $jqPlotTitle = $( ".jqplot-title", $trendContainer ) ;

            $( "button.csap-button-icon", $jqPlotTitle ).remove() ;

            let $viewButton = jQuery( '<button/>', {
                class: "csap-button-icon launch-window",
                title: "Open in Analytics Portal" } ) ;


            $jqPlotTitle.append( $viewButton ) ;
            $viewButton.off().click( function () {
                let urlAction = analyticsUrl + "&project=" + _linkProject + "&appId=" + APP_ID ;


                if ( trendItem.metric ) {
                    let metric = trendItem.metric ;
                    if ( trendItem.report && trendItem.report.startsWith( "custom" ) ) {
                        metric = "totalUsrCpu,totalSysCpu" ;
                    }
                    urlAction += `&metric=${metric}` ;
                }

                if ( trendItem.report ) {

                    let targetTrend = trendItem.report ;
                    if ( targetTrend == "host" ) {
                        targetTrend = "tableHost" ;
                    } else if ( targetTrend.startsWith( "custom" ) ) {
                        targetTrend = "tableHost" ;
                    } else if ( trendItem.serviceName ) {
                        targetTrend += "/detail" ;
                    }
                    urlAction += "&report=" + targetTrend ;
                }

                if ( trendItem.serviceName != undefined ) {
                    let targetService = trendItem.serviceName ;
                    if ( targetService.contains( "," ) ) {
                        console.log( "Picking first service in set: " + targetService ) ;
                        let services = targetService.split( "," ) ;
                        targetService = services[0] ;
                    }
                    urlAction += "&service=" + targetService ;
                }


                console.log( "Opening: " + urlAction ) ;

                openWindowSafely( urlAction, "_blank" ) ;
                return false ; // prevents link
            } ) ;

        } ;

        // initial registration
        trendEventsFunction() ;

        // reregister on redraws
        $( '#' + containerId ).off() ;
        $( '#' + containerId ).bind( 'jqplotClick',
                function ( ev, seriesIndex, pointIndex, data ) {
                    if ( isDisplayZoomHelpOnce ) {
                        isDisplayZoomHelpOnce = false ;
                        alertify.notify( "double click to reset zoom to original" ) ;
                    }
                    // graph re-rendering deletes the title and button - so redraw
                    trendEventsFunction() ;
                }
        ) ;
    }

    function calculateGraphMin( seriesToPlot ) {
        // set the min
        let lowestValue = -999 ;

        for ( let i = 0 ; i < seriesToPlot.length ; i++ ) {
            let lineSeries = seriesToPlot[i] ;
            for ( let j = 0 ; j < lineSeries.length ; j++ ) {
                let pointsOnLine = lineSeries[j] ;
                let current = pointsOnLine[1] ;
                if ( lowestValue == -999 ) {
                    lowestValue = current ;
                } else if ( current < lowestValue ) {
                    lowestValue = current ;
                }
            }
        }
        //console.log( "theMinForY pre lower: ", lowestValue) ;
        let loweredBy80Percent = lowestValue * 0.8 ;
        if ( loweredBy80Percent < 5 ) {
//            loweredBy80Percent = parseFloat( loweredBy80Percent.toFixed( 1 ) );
            loweredBy80Percent = Math.floor( loweredBy80Percent * 100 ) / 100 ;

        } else {
            loweredBy80Percent = Math.floor( loweredBy80Percent ) ;
        }



        //console.log( "theMinForY: " + lowestValue + " loweredBy80Percent: ", loweredBy80Percent );
        return loweredBy80Percent ;
    }

    function buildTrendConfiguration( showMarker, timeFormat ) {
        let config = {
            title: "placeholder",
            seriesColors: CSAP_THEME_COLORS,
            stackSeries: false,
            seriesDefaults: {
                fill: false,
                fillAndStroke: true,
                fillAlpha: 0.5,
                pointLabels: { //http://www.jqplot.com/docs/files/plugins/jqplot-pointLabels-js.html
                    show: false,
                    ypadding: 0
                },
                markerOptions: {
                    show: showMarker,
                    size: 3,
                    color: "#6495ED"
                },
                rendererOptions: {
                    smooth: true
                }
            },
            cursor: {
                show: false,
                tooltipLocation: 'nw',
                zoom: true
            },
            axesDefaults: {
                tickOptions: { showGridline: false }
            },
            axes: {
                xaxis: {
                    // http://www.jqplot.com/tests/date-axes.php
                    renderer: $.jqplot.DateAxisRenderer,
//                    min: new Date( ),
//                    max: new Date(),
                    tickOptions: { formatString: timeFormat }
                }

            },
            // http://www.jqplot.com/docs/files/plugins/jqplot-highlighter-js.html
            highlighter: {
                show: true,
                showMarker: true,
                sizeAdjust: 10,
                tooltipLocation: "n",
                tooltipOffset: 5,
                tooltipContentEditor: function ( str, seriesIndex, pointIndex, plot ) {
                    //the str is the ready string from tooltipFormatString
                    //depending on how do you give the series to the chart you can use plot.legend.labels[seriesIndex] or plot.series[seriesIndex].label
                    return '<b><span style="color:blue;">' + plot.legend.labels[seriesIndex] + ': </span></b>' + str ;
                },
                formatString: '<div class="trendSeries"></div>%s: <div class="tipValue">%s</div> <br><div class="trendOpen">Click and drag to zoom</div>' //%d
            },
            legend: {
                show: false,
                labels: "seriesLabel",
                location: 'ne',
                placement: "outside"
            }
        }

        return config ;
    }

    function trendHighlight( project ) {
        return ;
        console.log( "Project: " + project ) ;
        //$(".jqplot-highlighter-tooltip").hide() ;
        let updateTip = function () {
            $( ".trendSeries" ).text( project ) ;
        } ;

        // race condition - try twice
        setTimeout( updateTip, 100 ) ;
        setTimeout( updateTip, 500 ) ;
    }

    function has10kValue( valueArray ) {
        for ( let i = 0 ; i < valueArray.length ; i++ ) {
            if ( valueArray[i] > 10000 )
                return true ;
        }
        return false ;

    }

    function buildSeries( xArray, yArray ) {
        let graphPoints = new Array() ;

        let divideBy = 1 ;

        if ( has10kValue( yArray ) )
            divideBy = 1000
        for ( let i = 0 ; i < xArray.length ; i++ ) {
            let resourcePoint = new Array() ;
            resourcePoint.push( xArray[i] ) ;
            // resourcePoint.push(i  );
            //console.log("****** Rounding: " + yArray[i]/divideBy)
            resourcePoint.push( yArray[i] / divideBy ) ;

            graphPoints.push( resourcePoint ) ;
        }

        xArray = null ;
        yArray = null ;
        // console.log( "Points: " + JSON.stringify(graphPoints ) );

        return graphPoints ;
    }


} ) ;
