
import _dom from "../../utils/dom-utils.js";
import _net from "../../utils/net-utils.js";

import utils from "../utils.js"



const perfMeters = performance_meters();

export default perfMeters


function performance_meters() {

    _dom.logHead( "Module loaded" );

    const $performanceTab = $( "#performance-tab-content" );
    const $meterContent = $( "#performance-tab-meters", $performanceTab );
    const $meters = $( "#analyticsMeters", $meterContent );
    const $seletectedMeters = $( "#selected-meter", $meterContent );

    const $meterDetailTable = $( "#meter-detail-table", $meterContent );


    const $applicationLaunchers = $( "#applications", $meterContent );
    let _lastLaunchCount = 0;

    let _linkProject = null;
    let _activePlots = new Array();

    let _selectedHistogram = null;
    let _lastMeterTimeout;


    return {

        initialize: function () {
            //initialize() ;
            $( "#close-meter-details", $seletectedMeters ).click( function () {
                console.log( `back to meters summary, checked count: ${ $( ".meterInput:checked", $meters ).length }` );
                //                $(".meterInput:checked",$meters).closest(".selected").removeClass("selected") ;
                //                $(".meterInput:checked",$meters).show().prop("checked", false) ;

                //$meterDetailTable.trigger("destroy");
                $meters.toggle();
                $seletectedMeters.toggle();
                if ( !$( "#include-multiple-meters" ).is( ":checked" ) ) {
                    $( "#analyticsMeters", $meterContent ).empty();
                    _selectedHistogram = null;
                }
                getRealTimeMeters();
            } );

        },

        show: function ( forceHostRefresh, linkProject ) {
            _linkProject = linkProject;


            if ( forceHostRefresh ) {
                console.log( `Clearing selected meters` );
                $( ".meterInput:checked", $meters )
                    .prop( "checked", false );

                _selectedHistogram = null;

            }
            let contentLoadedPromise = getRealTimeMeters();

            getApplicationLaunchers();

            return contentLoadedPromise ;
        },
        renderMeter: function ( meterId, meterJson, checkBoxFunction ) {
            return renderMeter( meterId, meterJson, checkBoxFunction );
        },
        checkDivideBy: function ( meterJson, meterValue ) {
            return checkDivideBy( meterJson, meterValue );
        }

    }


    function rebindApplicationLaunchers() {

        $( "button", $applicationLaunchers ).off().click( function () {
            let $button = $( this );
            let serviceName = $button.data( "service" );
            let servicePath = $button.data( "path" );
            console.log( `launching ${ serviceName }` );
            utils.launchService( serviceName, servicePath );

        } );
    }

    function getApplicationLaunchers() {

        $.getJSON( `${ APP_BROWSER_URL }/launchers`, null ).done( buildAppLaunchButtons )

            .fail( function ( jqXHR, textStatus, errorThrown ) {
                console.log( "Error: Retrieving meter definitions ", errorThrown )
                // handleConnectionError( "Retrieving lifeCycleSuccess fpr host " + hostName , errorThrown ) ;
            } );


        //    

    }

    function buildAppLaunchButtons( launchers ) {
        console.log( `launchers`, launchers );

        if ( launchers.length != _lastLaunchCount ) {
            $applicationLaunchers.empty();
            _lastLaunchCount = launchers.length;

            for ( let launcher of launchers ) {

                let type = 'csap-button-icon csap-window';
                if ( launcher.service.includes( "grafana" )
                    || launcher.service.includes( "analytics" )
                    || ( launcher.label && launcher.label.toLowerCase().includes( "adoption" ) ) ) {
                    type = 'csap-button-icon csap-graph';

                } else if ( launcher.service.includes( "csap-agent" ) ) {
                    type = 'csap-button-icon csap-trademark';

                } else if ( launcher.label && launcher.label.toLowerCase().includes( "csap" ) ) {
                    type = 'csap-button-icon csap-play';

                } else if ( ( launcher.label && launcher.label.toLowerCase().includes( "event" ) )
                    || ( launcher.label && launcher.label.toLowerCase().includes( "log" ) ) ) {
                    type = 'csap-button-icon csap-logs';

                } else if ( launcher.service.includes( "kubelet" )
                    || launcher.service.includes( "mongo" ) ) {
                    type = 'csap-button-icon csap-indent';

                } else if ( launcher.service.includes( "prometh" )
                    || launcher.service.includes( "mongo" ) ) {
                    type = 'csap-button-icon csap-db';

                } else if ( launcher.service.includes( "alert" )
                    || ( launcher.label && launcher.label.toLowerCase().includes( "alert" ) ) ) {
                    type = 'csap-button-icon status-warning';
                }

                let label = launcher.service;
                if ( launcher.label ) {
                    label = launcher.label;
                }

                let desc = `Launching: ${ launcher.service }`;
                if ( launcher.description ) {
                    desc = launcher.description;
                }

                jQuery( '<button/>', {
                    class: type,
                    title: desc,
                    text: label,
                    "data-service": launcher.service,
                    "data-path": launcher.path
                } ).appendTo( $applicationLaunchers );
            }

            rebindApplicationLaunchers();
        }
    }


    function getRealTimeMeters() {


        console.log( "project: " + utils.getActiveProject() );
        clearTimeout( _lastMeterTimeout );


        let meters = new Array();

        //console.log( "meterInput:checked: " + $(".meterInput:checked").length ) ;
        $( ".meterInput:checked" ).each( function () {
            //console.log ( "pushing: " +  $(this).data("meter") ) ;

            if ( $( this ).is( ":checked" ) ) {
                meters.push( $( this ).data( "meter" ) );
            }
        } );


        let paramObject = {
            project: utils.getActiveProject(),
            meterId: meters
        };

        let viewLoadedPromise = _net.httpGet( `${ SERVICE_URL }/realTimeMeters`, paramObject );

        viewLoadedPromise
            .then( meterReport => {
                realTimeMetersSuccess( meterReport );

                if ( !$meterContent.is( ":visible" ) ) {
                    console.log( "getRealTimeMeters: page hidden, ending autorefresh" );
                    return;
                }

                _lastMeterTimeout = setTimeout( getRealTimeMeters, utils.getRefreshInterval() );
            } )
            .catch( ( e ) => {
                console.warn( e );
            } );;


        return viewLoadedPromise;


    }


    function realTimeMetersSuccess( responseJson ) {
        console.log( `realTimeMetersSuccess() - deleting previous meters, count: ${ _activePlots.length }` );
        for ( let i = 0; i < _activePlots.length; i++ ) {
            let thePlot = _activePlots.pop();

            //console.log("deleteing plot", thePlot ) ;
            thePlot.destroy();
            // delete thePlot ;

        }
        //console.log(" Success: " +  JSON.stringify(responseJson, null,"\t") ) 
        renderRealTimeMeters( responseJson )
    }


    function renderRealTimeMeters( responseJson ) {

        $( "#analyticsMeters", $meterContent ).empty();
        for ( let i = 0; i < responseJson.length; i++ ) {

            let meterId = "meter" + i;

            let meterJson = responseJson[ i ];
            // console.log( " meterJson: " +  JSON.stringify(meterJson, null,"\t") ) 
            if ( !renderMeter( meterId, meterJson, meterSelected ) )
                continue;

            meterClickRegistration( meterId, meterJson )
        }

        addToMetricTable( responseJson );

        addSelectedHistogram( responseJson );


    }

    function meterSelected( selectedHistogram ) {
        console.log( "meterSelected() " + selectedHistogram );
        _selectedHistogram = selectedHistogram;
        getRealTimeMeters();
    }
    // note the required closure
    function meterClickRegistration( meterId, meterJson ) {


        $( '#' + meterId, $meterContent ).click( function () {

            console.log( `Show selected meter` );
            $meters.toggle();
            $seletectedMeters.toggle();
            $( "input", $( this ).parent() ).trigger( "click" );

            //            let targetUrl = "MeterActivity?" + "&project=" + _linkProject + "&meterId=" + meterJson.id ;
            //            openWindowSafely( targetUrl, "_blank" ) ;
            //
            //            return false ; // prevents link
        } );
    }

    function renderMeter( meterId, meterDefinition, checkBoxFunction ) {

        console.debug( `renderMeter() meterId:  ${ meterId }, definition: ${ meterDefinition.label }` );


        if ( meterDefinition.test ) {
            meterDefinition.value = 1;
            meterDefinition.min = 1;
            meterDefinition.intervals = [ 1.0, 0 ]
            meterDefinition.reverseColors = true;
        }

        let intervalNums = [ 10, 20, 30 ];
        let intervalsSpecified = meterDefinition.intervals;

        if ( Array.isArray( intervalsSpecified ) && intervalsSpecified.length == 3 ) {

            let defaultValue = 10;
            intervalNums = intervalsSpecified.map( function ( item ) {
                let parsedValue = parseInt( item ) || defaultValue++;
                return parsedValue;
            } );
        }
        //console.log( " intervalNums: " +  JSON.stringify(intervalNums, null,"\t") ) 
        if ( meterDefinition.value == undefined || meterDefinition[ "host-count" ] == undefined || meterDefinition[ "host-count" ] == 0 ) {
            console.debug( "No value found for meter, skipping: " + meterDefinition.id );
            return false;
        }

        let meterTitle = "Click to open Real Time Performance Dashboard"
        if ( checkBoxFunction ) {
            meterTitle = "Click to view last collected value per host";
        }

        let $containerDiv = jQuery( '<div/>', {
            id: meterId + "Container",
            class: "meterContainer",
            title: meterTitle
        } );


        let $checkedInput = null;
        if ( checkBoxFunction ) {
            $checkedInput = jQuery( '<input/>', {
                id: meterId + "Input",
                class: "meterInput",
                type: "checkbox",
                "data-meter": meterDefinition.id
            } ).change( function () {

                let selectedHistogram = $( ".meterInput:checked" ).first().data( "meter" );
                if ( $( this ).prop( "checked" ) ) {
                    selectedHistogram = $( this ).data( "meter" );
                }

                checkBoxFunction( selectedHistogram );
            } );

            $containerDiv.append( $checkedInput );
        }

        // checkedInput.data("metric", meterJson.id );



        let $meterDiv = jQuery( '<div/>', {
            id: meterId,
            class: "meterGraph"
        } );
        $containerDiv.append( $meterDiv );

        //console.log("Checking meterIdParam: ", meterIdParam);
        if ( checkBoxFunction && ( ( meterDefinition.id == _selectedHistogram ) || meterDefinition.hostNames ) ) {
            $checkedInput.attr( "checked", "checked" );
            $containerDiv.addClass( "selected" );
        }

        if ( meterDefinition.healthMeter && Array.isArray( meterDefinition.healthMeter ) && meterDefinition.healthMeter.length == 3 ) {

            let showHealthy = meterDefinition.healthMeter[ 0 ];
            let meterCurrent = checkDivideBy( meterDefinition, meterDefinition.value );
            let warningIfLessThan = meterDefinition.healthMeter[ 1 ];
            let warningIfMoreThan = meterDefinition.healthMeter[ 2 ];
            let meterType = null;
            let $label = jQuery( '<div/>', { class: "meter-label", text: meterDefinition.label } );
            let $meterValue = jQuery( '<div/>', { class: "meter-value", text: "Collected: " + meterCurrent } );
            if ( meterCurrent < warningIfLessThan ) {
                meterType = "meter-warning";
                $meterValue.append( ", min: " + warningIfLessThan );
            } else if ( meterCurrent > warningIfMoreThan ) {
                meterType = "meter-warning";
                $meterValue.append( ", max: " + warningIfMoreThan );
            } else if ( showHealthy ) {
                meterType = "meter-ok";
            }

            if ( meterType != null ) {
                $meterDiv.addClass( meterType );


                $meterDiv.append( $meterValue );
                $meterDiv.append( $label );
                $meterDiv.css( "height", "130px" ).css( "overflow", "hidden" );
                $meters.append( $containerDiv );
                return true;

            }
        }



        $meters.append( $containerDiv );

        let meterValue = meterDefinition.value;

        if ( meterDefinition.id == "jmxCustom.httpd.UrlsProcessed" ) {
            // meterValue = meterValue * 100; // testing only
        }
        // console.log( "meterJson: ", meterJson );

        meterValue = checkDivideBy( meterDefinition, meterValue );

        if ( meterDefinition.multiplyBy != undefined ) {
            meterValue = ( meterValue * meterDefinition.multiplyBy ).toFixed( 1 );
        }


        let meterLabel = meterDefinition.label, meterMin = meterDefinition.min, meterMax = meterDefinition.max;

        // test meter levels
        //		if (meterLabel === "Http Requests Per Minute") {
        //			meterValue=800;
        //			intervalNums = [8000, 11000, 15000] ;
        //			console.log("\n\n\n TESTING   intervalNums: ", intervalNums) ;
        //		}
        //		if (meterLabel === "VM coresActive") {
        //			console.log("\n\n\n TESTING") ;
        //			intervalNums = [3, 10, 15] ;
        //			meterValue=50;
        //			console.log("\n\n\n TESTING   intervalNums: ", intervalNums) ;
        //		}

        let maxInterval = intervalNums[ intervalNums.length - 1 ];

        if ( meterValue > ( maxInterval ) ) {
            // meters will not display when interval is less then value.
            intervalNums[ intervalNums.length - 1 ] = meterValue * 1.2;
        }
        if ( meterValue > ( maxInterval * 3 ) ) {
            // when a big delta - adjust the ui to make reading current value easier
            meterMin = Math.round( meterValue / 6 );
            intervalNums[ intervalNums.length - 3 ] = meterMin;
            intervalNums[ intervalNums.length - 2 ] = meterMin;
        }


        let meterColors = [ '#66cc66', '#E7E658', '#cc6666' ];
        if ( meterDefinition.reverseColors ) {
            meterColors = [ '#cc6666', '#E7E658', '#66cc66' ];
        }
        // console.log( " maxInterval:" + maxInterval) ;



        if ( !meterDefinition.disableScaling ) {
            let ROUNDING = 1000;
            if (
                meterValue > ( 3 * ROUNDING ) ||
                intervalNums[ 2 ] > ( 3 * ROUNDING )
            ) {
                meterValue = Math.round( meterValue / ROUNDING );
                for ( let i = 0; i < intervalNums.length; i++ ) {
                    intervalNums[ i ] = intervalNums[ i ] / ROUNDING;
                }
                meterMin = Math.round( intervalNums[ 0 ] );
                if ( meterValue < intervalNums[ 0 ] ) {
                    meterMin = Math.round( meterValue * 0.7 );
                }

                meterLabel += '<span style="color: red; position: absolute; top: -100px; right: -10px">(1000s)</span>';
                //meterMin = Math.round( meterMin / ROUNDING ) ;
                //meterMax = ( meterMax / ROUNDING ).toFixed( 1 ) ;
            }
        }
        for ( let i = 0; i < intervalNums.length; i++ ) {
            intervalNums[ i ] = Math.round( intervalNums[ i ] );
        }

        // console.log("meterValue: " + meterValue + "  intervalNums: ", intervalNums , " meterMin: ", meterMin,  " meterMax: ", meterMax) ;

        if ( meterDefinition.useHealth ) {

        }

        let thePlot = $.jqplot( meterId, [ [ meterValue ] ], {
            title: '',

            grid: {
                backgroundColor: "transparent"
            },
            seriesDefaults: {
                renderer: $.jqplot.MeterGaugeRenderer,
                rendererOptions: {
                    label: meterLabel,
                    labelPosition: "bottom",
                    min: meterMin,
                    //					max: meterMax,
                    padding: 0,
                    intervals: intervalNums,
                    intervalColors: meterColors
                }
            }
        } );

        _activePlots.push( thePlot );



        //  meter gauge leaves lots of padding on the bottom
        $meterDiv.css( "height", "130px" ).css( "overflow", "hidden" );
        // meterDiv.css( "width", "200px" ).css( "overflow", "hidden" );

        return true;
    }

    function checkDivideBy( meterJson, meterValue ) {

        if ( meterJson.divideBy != undefined ) {

            if ( meterJson.divideBy == "host-count" ) {
                console.debug( `${ meterJson.label } Dividing  ${ meterValue } by meterJson.host-count:  ${ meterJson[ "host-count" ] }` );
                meterValue = ( meterValue / meterJson[ "host-count" ] ).toFixed( 2 );
            } else {
                meterValue = ( meterValue / meterJson.divideBy ).toFixed( 2 );
            }
            console.debug( `meterValue: ${ meterValue }` );

            if ( isNaN( meterValue ) )
                return 0;
        }

        return meterValue;
    }


    function addSelectedHistogram( responseJson ) {
        let $browserFrame = $( "#meterHistogram", $meterContent );

        if ( $( ".meterInput:checked", $meters ).length == 0 ) {
            // alertify.notify( "No meters are selected" ) ;
            $browserFrame.hide();
            console.log( "no inputs checked, _selectedHistogram: ", _selectedHistogram );
            return;
        }
        $browserFrame.show();

        let heightAdjust = 1;
        let seriesTitles = [];
        let seriesColorsArray = [ "#05B325" ];
        let selectedMeter = null;


        let lastMeter = null;
        for ( let i = 0; i < responseJson.length; i++ ) {
            let meterJson = responseJson[ i ];
            lastMeter = meterJson;

            if ( !meterJson.hostNames )
                continue;
            if ( !meterJson.hostValues )
                continue;

            console.log( "addSelectedHistogram() : selectedMetricId: " + _selectedHistogram
                + " current: " + meterJson.id );
            if ( meterJson.id == _selectedHistogram ) {
                seriesTitles = [ meterJson.label ];
                selectedMeter = meterJson;
                break;
            }

        }


        if ( selectedMeter == null ) {
            console.log( "There is no data for selected meter in current project: " + _selectedHistogram );
            // alertify.alert( "There is no data for selected meter in current project: " + _selectedHistogram ) ;
            return;
        }

        // ugly sorting of 2 related arrays
        let list = [];
        for ( let j = 0; j < selectedMeter.hostNames.length; j++ ) {
            list.push( { 'val': selectedMeter.hostValues[ j ], 'host': selectedMeter.hostNames[ j ] } );
        }

        list.sort( function ( a, b ) {
            return ( ( a.val < b.val ) ? -1 : ( ( a.val == b.val ) ? 0 : 1 ) );
        } );

        let sortedVals = new Array();
        let sortedLabels = new Array();
        for ( let k = 0; k < list.length; k++ ) {

            let meterValue = list[ k ].val;

            if ( lastMeter.divideBy != undefined && lastMeter.divideBy != "host-count" )
                meterValue = ( meterValue / lastMeter.divideBy ).toFixed( 1 );

            //meterValue = realTimeMeter.checkDivideBy( meterJson, meterValue );
            if ( lastMeter.multiplyBy != undefined )
                meterValue = ( meterValue * lastMeter.multiplyBy ).toFixed( 1 );

            sortedVals.push( Number( meterValue ) );
            sortedLabels.push( list[ k ].host );
        }


        // console.log( "Plot vals: " +  JSON.stringify(sortedVals, null,"\t")  + " labels: " +  JSON.stringify(sortedLabels, null,"\t") ) ;
        let seriesToPlot = [ sortedVals ];
        let seriesLabel = sortedLabels;

        let hightlightColorArray = new Array();
        for ( let i = 0; i < seriesTitles.length; i++ ) {
            hightlightColorArray.push( "black" );
        }

        $browserFrame.empty();

        let $histogramContainer = jQuery( '<div/>', {
            id: "meter-histogram-plot",
            title: "Last metric selected will be shown on histogram",
            class: ""
        } );

        let tableHeight = $( "#meterTable", $meterContent ).outerHeight();
        $histogramContainer.css( "height", Math.round( tableHeight - 40 ) );
        // .css( "width", "400px" ).css( "height", ( ( seriesLabel.length + 5 ) * heightAdjust ) + "em" )

        $browserFrame.append( $histogramContainer );


        $.jqplot( "meter-histogram-plot", seriesToPlot, {
            stackSeries: false,
            animate: !$.jqplot.use_excanvas,
            seriesColors: seriesColorsArray,

            title: {
                text: seriesTitles[ 0 ]
            },

            axesDefaults: {
                tickOptions: {
                    fontSize: '12pt'
                }
            },
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
                    ticks: seriesLabel,
                    renderer: $.jqplot.CategoryAxisRenderer
                }
            },
            highlighter: { show: false },
            legend: {
                labels: seriesTitles,
                location: "nw",
                //placement: "insideGrid",
                show: false
            }
        } );
    }


    function addToMetricTable( meterReports ) {

        let $browserFrame = $( "#meterTable", $meterContent );

        if ( $( ".meterInput:checked", $meters ).length == 0 ) {
            // alertify.notify( "No meters are selected" ) ;
            $browserFrame.hide();
            console.log( "no inputs checked, _selectedHistogram: ", _selectedHistogram );
            return;
        }
        $browserFrame.show();

        addTablePlaceHolders( meterReports );

        $( ".hostRow", $meterDetailTable ).hide();
        $( ".metricColumn", $meterDetailTable ).hide();
        for ( let meterReport of meterReports ) {

            if ( !meterReport.hostNames ) {
                continue;
            }

            let metricClass = utils.buildValidDomId( meterReport.label ); //( meterReport.id ).replace( /\./g, '_' ) ;
            $( "." + metricClass, $meterDetailTable ).show();

            for ( let hostIndex = 0; hostIndex < meterReport.hostNames.length; hostIndex++ ) {
                let curHost = meterReport.hostNames[ hostIndex ];
                let meterValue = meterReport.hostValues[ hostIndex ];

                if ( meterReport.divideBy != undefined && meterReport.divideBy != "host-count" )
                    meterValue = ( meterValue / meterReport.divideBy ).toFixed( 1 );

                if ( meterReport.multiplyBy != undefined )
                    meterValue = ( meterValue * meterReport.multiplyBy ).toFixed( 1 );


                let hostRowId = ( curHost + "Row" );
                $( "#" + hostRowId ).show();

                let metricHostClass = utils.buildValidDomId( meterReport.label + curHost ); // ( meterReport.id + curHost ).replace( /\./g, '_' ) ;

                let $metricLink = $( "." + metricHostClass + " a", $meterDetailTable );
                let oldValue = $metricLink.text();
                $metricLink.text( meterValue );

                // console.log(`meterValue: '${ meterValue }', oldValue: '${ oldValue }' `) ;

                if ( oldValue != meterValue ) {
                    if ( oldValue !== "-" ) {
                        utils.flash( $metricLink );
                    }
                } else {
                    // console.log(`unflashing`) ;
                    utils.flash( $metricLink, false );
                }


            }
        }

        //$.tablesorter.computeColumnIndex( $( "tbody tr", $meterDetailTable ) ) ;
        $meterDetailTable.trigger( "updateAll" );
        //$meterDetailTable.trigger('sortReset');
    }


    function addTablePlaceHolders( meterDefinitions ) {
        // add headers and footers	


        let sortColumn = 0;
        let tableUpdated = false;
        for ( let meterReport of meterDefinitions ) {


            let metricClass = utils.buildValidDomId( meterReport.label );
            if ( $( "th." + metricClass ).length == 0 ) {
                console.debug( `addTablePlaceHolders: ${ metricClass }` );

                // console.log( "addToMetricTable() Adding header: " + metricClass ) ;
                $meterDetailTable.trigger( "destroy" );

                let headerColumn = jQuery( '<th/>', {
                    class: "num metricColumn " + metricClass,
                    text: meterReport.label
                } )

                $( "thead tr", $meterDetailTable ).append( headerColumn );

                let footColumn = jQuery( '<td/>', {
                    class: "num metricColumn " + metricClass,
                    "data-math": "col-sum"
                } )

                $( "tfoot .totalRow", $meterDetailTable ).append( footColumn );

                let meanColumn = jQuery( '<td/>', {
                    class: "num metricColumn " + metricClass,
                    "data-math": "col-mean"
                } )

                $( "tfoot .meanRow", $meterDetailTable ).append( meanColumn );
                tableUpdated = true;
            }

            if ( !meterReport.hostNames ) {
                continue;
            }

            for ( let hostNameForData of meterReport.hostNames ) {
                // Add host Row if not present

                let hostRowId = ( hostNameForData + "Row" );
                // console.log( "addToMetricTable():  hostRowId: " + hostRowId ) ;

                for ( let hostColIndex = 0; hostColIndex < meterDefinitions.length; hostColIndex++ ) {
                    let rowJson = meterDefinitions[ hostColIndex ];
                    if ( rowJson.id === _selectedHistogram ) {
                        sortColumn = hostColIndex + 1;
                        let sortCols = [ [ sortColumn, 1 ] ]
                        if ( $meterDetailTable[ 0 ].config ) {
                            // $meterDetailTable.find( `th:eq( ${sortColumn} )`).trigger('sort');
                            // update sort column based on latest selection
                            $.tablesorter.sortOn( $meterDetailTable[ 0 ].config, sortCols );
                        }
                    }
                }

                if ( $( "#" + hostRowId ).length == 0 ) {

                    let hostRow = jQuery( '<tr/>', {
                        id: hostRowId,
                        class: "hostRow "
                    } )

                    let hostCol = jQuery( '<td/>', {
                        text: hostNameForData,
                    } ).appendTo( hostRow );

                    $( "tbody", $meterDetailTable ).append( hostRow );

                    for ( let rowJson of meterDefinitions ) {

                        let metricClass = utils.buildValidDomId( rowJson.label ); // ( rowJson.id ).replace( /\./g, '_' ) ;
                        let metricHostClass = utils.buildValidDomId( rowJson.label + hostNameForData ); //( rowJson.id + hostNameForData ).replace( /\./g, '_' ) ;
                        let $metricColumn = jQuery( '<td/>', {
                            class: "num metricColumn " + metricClass + " " + metricHostClass
                        } )

                        let paramArray = rowJson.id.split( "." );
                        let meterType = paramArray[ 0 ];

                        console.debug( `Adding id: ${ rowJson.id }, meterType: ${ meterType }` );

                        let $meterLaunchButton = jQuery( '<a/>', {
                            href: "#open-dashboard",
                            class: "csap-link",
                            text: `-`
                        } ).appendTo( $metricColumn );

                        let params = {};
                        let path = "/app-browser#agent-tab,system";
                        if ( meterType === METRICS_APPLICATION ) {
                            let serviceName = paramArray[ 1 ];
                            params = { defaultService: serviceName };
                            path = "/app-browser#agent-tab,application";

                        } else if ( meterType === METRICS_JAVA ) {
                            let serviceName = paramArray[ 1 ].substring( paramArray[ 1 ].indexOf( "_" ) + 1 );
                            params = { defaultService: serviceName };
                            path = "/app-browser#agent-tab,java";

                        } else if ( meterType === METRICS_OS_PROCESS ) {
                            let serviceName = paramArray[ 1 ].substring( paramArray[ 1 ].indexOf( "_" ) + 1 );
                            params = { defaultService: serviceName };
                            path = "/app-browser#agent-tab,service";
                        }

                        $meterLaunchButton.click( function () {
                            utils.openAgentWindow( hostNameForData, path, params );
                            return false;
                        } );


                        $meterLaunchButton.css( "padding", "3px" );
                        hostRow.append( $metricColumn );
                    }
                }
            }
        }

        if ( tableUpdated ) {
            configureTables( sortColumn );
        }
    }

    function configureTables( sortColumn ) {

        console.log( "sorting on: ", sortColumn );

        $meterDetailTable.tablesorter( {
            sortList: [ [ sortColumn, 1 ] ],
            theme: 'csapSummary',
            widgets: [ 'math' ],
            widgetOptions: {
                math_mask: '##0',
                math_data: 'math',
                columns_tfoot: false,
                math_complete: tableMathFormat
            }
        } );

    }
    function tableSorterMathFormat( $cell, wo, result, value, arry ) {
        //var txt = '<span class="align-decimal">' + result + '</span>';
        var txt = result;
        if ( $cell.attr( 'data-prefix' ) != null ) {
            txt = $cell.attr( 'data-prefix' ) + txt;
        }
        if ( $cell.attr( 'data-suffix' ) != null ) {
            txt += $cell.attr( 'data-suffix' );
        }
        return txt;
    }
    function tableMathFormat( $cell, wo, result, value, arry ) {
        let txt = '<span class="align-decimal">' + result + '</span>';
        if ( $cell.attr( 'data-prefix' ) != null ) {
            txt = $cell.attr( 'data-prefix' ) + txt;
        }
        if ( $cell.attr( 'data-suffix' ) != null ) {
            txt += $cell.attr( 'data-suffix' );
        }
        return txt;
    }

}