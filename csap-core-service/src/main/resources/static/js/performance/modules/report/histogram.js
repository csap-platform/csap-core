define( [ "browser/utils", "model", "windowEvents", "./dataService", "./utils" ], function ( csapUtils, model, windowEvents, dataService, utils ) {

    console.log( "Module loaded: reports/histogram" ) ;
    let hiddenServiceNames = new Object() ;


    return {
        //
        loadData: function ( currentResults, reportId ) {
            generateAttributeHistogram( currentResults, reportId ) ;
        },
        //
    }



    function generateAttributeHistogram( currentResults, reportId ) {

        let userSelectedAttributes = $( "#visualizeSelect" ).val().split( "," ) ;

        let primaryAttribute = userSelectedAttributes[0] ;
        let labelArray = new Array() ;
        let valueArray = new Array() ;
        let deltaArray = new Array() ;

        console.log( `generateAttributeHistogram() : reportId '${reportId}', primaryAttribute: ${primaryAttribute} `, currentResults ) ;

        let numSamples = 0 ;

        // Sort by host
        if ( $( "#histogramSort" ).val() == "metric" ) {
            currentResults.data.sort( function ( a, b ) {
                // console.log( "b[selectedColumn]: " + b[selectedColumn])  ;

                let useInstanceTotal = false ;
                if ( reportId.includes( "os-process" )
                        && $( "#show-instance-summary" ).is( ":checked" ) ) {
                    useInstanceTotal = true ;
                }

                if ( reportId == "userid" || $( "#isUseTotal" ).is( ':checked' ) || useInstanceTotal ) {
                    return a[primaryAttribute] - b[primaryAttribute] ;
                }
                return a[primaryAttribute] / a.numberOfSamples - b[primaryAttribute] / b.numberOfSamples ;
            } ) ;
        } else {

            let labelSort = "" ;
            if ( reportId == METRICS_HOST ) {
                labelSort = "hostName" ;
            } else if ( reportId == "userid" ) {
                labelSort = "uiUser" ;
            } else if ( reportId.indexOf( "detail" ) != -1 ) {
                labelSort = "host" ;
            } else {
                labelSort = "serviceName" ;
            }

            currentResults.data.sort( function ( a, b ) {
                return b[ labelSort ].toLowerCase().localeCompare( a[ labelSort ].toLowerCase() ) ;
            } ) ;

        }

        // console.log("generateAttributeHistogram() Sorted :" + selectedColumn + " " +  JSON.stringify(currentResults.data, null,"\t") ) ;



        let hightlightColorArray = new Array() ;
        let rowData ;
        let isFirstRow = true ;
        for ( rowData of  currentResults.data ) {
            hightlightColorArray.push( "black" ) ;
            let rowLabel = "" ;
            // console.log("generateAttributeHistogram() divideBy: " + divideBy + " usTotal: "
            // + $("#isUseTotal").is(':checked'));

            if ( reportId == METRICS_HOST ) {
                if ( !model.isHostInSelectedCluster( rowData.hostName ) )
                    continue ;
                rowLabel = rowData.hostName ;
            } else if ( reportId == "userid" ) {
                console.log( `rowJson.uiUser: ${rowData.uiUser}` )
                if ( rowData.uiUser == null || rowData.uiUser == "System"
                        || rowData.uiUser == "agentUser" ) {
                    continue ;
                }
                rowLabel = rowData.uiUser ;
                ;

            } else if ( reportId.indexOf( "detail" ) != -1 ) {
                if ( !model.isHostInSelectedCluster( rowData.host ) )
                    continue ;
                rowLabel = rowData.host ;
            } else {
                if ( !model.isServiceInSelectedCluster( rowData.serviceName ) )
                    continue ;

                rowLabel = rowData.serviceName ;



            }

            let $container = jQuery( '<span/>', { } ) ;

            let hideDef = {
                class: "hide-service-button",
                type: "checkbox",
                title: "Click to include service",
                "data-name": rowLabel } ;

            if ( !hiddenServiceNames[rowLabel] ) {
                hideDef.checked = "checked" ;
            }
            let $hideButton = jQuery( '<input/>', hideDef ) ;
            $container.append( $hideButton ) ;

            labelArray.push( `<label class="hide-service-histo">${rowLabel}${ $container.html() }</label> ` ) ;

            numSamples += rowData.numberOfSamples ;

            // service/detail and jmx detail both need label
            if ( isFirstRow && reportId.contains( "detail" ) ) {
                isFirstRow = false ;
                $( "#serviceLabel" ).html(
                        rowLabel + " : " + primaryAttribute ) ;
            }

            let rowValue = calculateHistogramValue(
                    reportId,
                    userSelectedAttributes,
                    rowData ) ;
            if ( hiddenServiceNames[rowLabel] ) {
                console.log( `Suppressing ${rowLabel}, value is: ${ rowValue }` ) ;
                rowValue = 0 ;
            }

            valueArray.push( rowValue ) ;

            if ( dataService.isCompareSelected() ) {
                let deltaRow = null ;
                if ( rowData.host != undefined ) {
                    deltaRow = dataService.findCompareMatch( "host", rowData.host ) ;

                } else if ( rowData.hostName != undefined ) {
                    deltaRow = dataService.findCompareMatch( "hostName", rowData.hostName ) ;

                } else {
                    deltaRow = dataService.findCompareMatch( "serviceName", rowData.serviceName ) ;

                }

//				console.log( "deltaRow ", deltaRow )
                // console.log("Adding delta row") ;

                if ( deltaRow ) {
                    let deltaValue = calculateHistogramValue( reportId, userSelectedAttributes, deltaRow ) ;
                    //console.log( `${rowData.serviceName} Extracted ${ deltaValue } from row`, deltaRow) ;
                    deltaArray.push( deltaValue ) ;
                } else {
                    console.warn( `No match found for service: ${rowData.serviceName} or host ${ rowData.host }` ) ;
                    deltaArray.push( 0 ) ;
                }


//				console.log( "deltaArray ", deltaArray );
            }
        }

        utils.updateSampleCountDisplayed( numSamples ) ;

        let heightAdjust = 0.75 ;
        let seriesToPlot = [ valueArray ] ;

        let primaryLabel = $( "#reportStartInput" ).val() ;
        if ( primaryLabel == "" ) {
            primaryLabel = "Today" ;
        }

        let seriesLabel = [ primaryLabel ]
        let seriesColorsArray = [ CSAP_THEME_COLORS[0] ] ;


        console.log( `dataService.isCompareSelected(): ${dataService.isCompareSelected()}` )
        if ( dataService.isCompareSelected() ) {
            heightAdjust = 1.25 ;
            seriesToPlot = [ valueArray, deltaArray, ]
            seriesLabel = [ $( "#compareStartInput" ).val(), primaryLabel ]
            seriesColorsArray = [ CSAP_THEME_COLORS[1], CSAP_THEME_COLORS[0] ] ;
        }


        // console.log( "seriesToPlot ", JSON.stringify( seriesToPlot, null, "\t" ) );


        // $("#reportOptions").append(meterDiv) ;

        let $targetContainer = $( "#hostDiv .metricHistogram" ) ;
        if ( reportId != "host" ) {
            $targetContainer = $( "#os-processDiv .metricHistogram" ) ;
        }
        if ( reportId == "userid" ) {
            $targetContainer = $( "#useridDiv .metricHistogram" ) ;
        }
        $targetContainer.show() ;

        $targetContainer.append( $( "#metricsTrendingContainer" ) ) ;
        $( "#metricsTrendingContainer" ).show() ;


        let launchType = "host" ;
        if ( rowData.host == undefined && rowData.hostName == undefined ) {
            launchType = "service" ;
        }
        let $plotContainer = jQuery( '<div/>', {
            id: "metricPlot",
            title: "Click on bar to view " + launchType + " graphs",
            class: "meterGraph"
        } ).css( "height", ( ( labelArray.length + 5 ) * heightAdjust ) + "em" ) ;

        $plotContainer.css( "width",
                Math.round( $targetContainer.outerWidth( false ) ) - csapUtils.jqplotLegendOffset() ) ;


        $targetContainer.append( $plotContainer ) ;

        // $.jqplot("metricPlot", [[itemValueToday], [itemValueWeek]], {

        console.log( `generateAttributeHistogram() jqplot ` ) ;
        $.jqplot( "metricPlot", seriesToPlot, {
            stackSeries: false,
            animate: !$.jqplot.use_excanvas,
            seriesColors: seriesColorsArray,
            seriesDefaults: {
                renderer: $.jqplot.BarRenderer,
                pointLabels: {
                    show: true,
                    ypadding: 0
                },
                rendererOptions: {
                    barDirection: 'horizontal',
                    barPadding: 2,
                    barMargin: 2,
                    varyBarColor: false,
                    highlightColors: hightlightColorArray,
                    shadow: false
                }
            },
            axes: {
                yaxis: {
                    ticks: labelArray,
                    renderer: $.jqplot.CategoryAxisRenderer
                }
            },
            highlighter: { show: false },
            legend: {
                labels: seriesLabel,
                placement: "outside",
                show: true
            }
        } ) ;
        if ( dataService.isCompareSelected() ) {
            $( ".jqplot-point-label" ).css( "font-size", "0.6em" ) ;
        } else {
            $( ".jqplot-point-label" ).css( "font-size", "0.85em" ) ;
        }

        $( ".hide-service-button" ).off().click( function () {
            let serviceToHide = $( this ).data( "name" ) ;
//            alertify.alert( `Ignoring ${ serviceToHide  }` ) ;
            if ( !hiddenServiceNames[serviceToHide] ) {
                hiddenServiceNames[serviceToHide] = true ;
            } else {
                hiddenServiceNames[serviceToHide] = false ;
            }
            $( "#visualizeSelect" ).trigger( "change" ) ;

        } ) ;

        $( '#metricPlot' ).bind(
                'jqplotDataClick',
                function ( ev, seriesIndex, pointIndex, data ) {
                    // console.log("Clicked seriesIndex: " + seriesIndex + "
                    // pointIndex: " + pointIndex + " graph: " + data) ;
                    let $locateDiv = jQuery( '<div/>', { html: labelArray[pointIndex] } ) ;
                    let launchTarget = $( "input", $locateDiv ).data( `name` ) ;
                    console.log( `item clicked:  ${ launchTarget } reportId: ${ reportId} ` ) ;

                    if ( reportId == "userid" ) {
                        openWindowSafely( utils.getUserUrl( "userid=" + launchTarget ),
                                "_blank" ) ;

                    } else if ( reportId == "service" ) {
                        utils.launchServiceOsReport( launchTarget ) ;

                    } else if ( reportId == METRICS_JAVA ) {
                        utils.launchServiceJmxReport( launchTarget )
                    } else {
                        let hostUrl = uiSettings.analyticsUiUrl + "?host=" + launchTarget
                                + "&life=" + $( "#lifeSelect" ).val() + "&appId="
                                + $( "#appIdFilterSelect" ).val() + "&project="
                                + model.getSelectedProject() ;

                        if ( primaryAttribute == "totActivity" ) {
                            hostUrl = utils.getUserUrl( "hostName=" + launchTarget )
                        }

                        openWindowSafely( hostUrl, "_blank" ) ;
                    }
                } ) ;

    }





    function calculateHistogramValue( reportId, attributes, rowData ) {


        let useInstanceTotal = false ;
        if ( reportId.includes( "os-process" )
                && $( "#show-instance-summary" ).is( ":checked" ) ) {
            useInstanceTotal = true ;
        }

        // console.log( `calculateHistogramValue()  reportId: ${ reportId} useInstanceTotal: ${useInstanceTotal}`, attributes,  rowData ) ;
        let total = 0 ;
        for ( let attributeKey of attributes ) {
            let divideBy = 1 ;
            if ( ( reportId != "userid" )
                    && !$( "#isUseDailyTotal" ).is( ':checked' )
                    && attributeKey.indexOf( "Avg" ) == -1 ) {
                divideBy = rowData.numberOfSamples ;

            }

            if ( useInstanceTotal ) {
                let numberOfInstances = 1 ;
                if ( rowData.countCsapMean ) {
                    numberOfInstances = rowData.countCsapMean ;
                } else {
                    let numberOfSamplesIn24Hours = 24 * 60 * 2 ;
                    numberOfInstances = rowData.numberOfSamples / numberOfSamplesIn24Hours ;
                }
                divideBy = rowData.numberOfSamples / numberOfInstances ;

                console.debug( `calculateHistogramValue() ${ attributeKey } numberOfInstances:  ${numberOfInstances }` ) ;

            } else if ( !$( "#nomalizeContainer" ).is( ":visible" ) ) {
                divideBy = divideBy * ( 1 / $( "#nomalizeContainer select" ).val() ) ;
            }

//           console.log(`calculateHistogramValue() ${ attributeKey } divideBy:  ${divideBy }`) ;
            if ( attributeKey == "totalMemFree" || attributeKey == "memoryInMbAvg"
                    || attributeKey == "SwapInMbAvg" ) {

                return rowData[attributeKey] / divideBy / 1024 ;
            }
            total += rowData[attributeKey] / divideBy ;
        }

        return total ;
    }


} ) ;