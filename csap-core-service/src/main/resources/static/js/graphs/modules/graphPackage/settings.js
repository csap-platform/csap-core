define( [ "./graphLayout", "csapflot" ], function ( graphLayout, csapflot ) {

    console.log( "Module loaded: graphPackage/settings" ) ;
    let startButtonUrl = uiSettings.baseUrl + "images/16x16/play.svg" ;
    let pauseButtonUrl = uiSettings.baseUrl + "images/16x16/pause.png" ;
    
    

    let isKeepPlaying = true ;

    return {
        //
        uiComponentsRegistration: function ( resourceGraph ) {

            miscSetup( resourceGraph ) ;

            zoomSetup( resourceGraph ) ;

            layoutSetup( resourceGraph ) ;

            datePickerSetup( resourceGraph ) ;

            numberOfDaysSetup( resourceGraph ) ;
        },
        //
        addCustomViews: function ( resourceGraph, customViews ) {
            addCustomViews( resourceGraph, customViews ) ;
        },
        //
        dialogSetup: function ( settingsChangedCallback, $GRAPH_INSTANCE ) {

            dialogSetup( settingsChangedCallback, $GRAPH_INSTANCE ) ;
        },
        //
        modifyTimeSlider: function ( $newGraphContainer, sampleTimeArray, descTimeArray, resourceGraph ) {
            modifyTimeSlider( $newGraphContainer, sampleTimeArray, descTimeArray, resourceGraph ) ;
        },
        //
        addToolsEvents: function () {
            addToolsEvents() ;
        },
        //
        addContainerEvents: function ( resourceGraph, container ) {
            addContainerEvents( resourceGraph, container ) ;
        },
        postDrawEvents: function ( $newGraphContainer, $GRAPH_INSTANCE, numDays ) {
            postDrawEvents( $newGraphContainer, $GRAPH_INSTANCE, numDays ) ;
        }
    } ;

    function miscSetup( resourceGraph ) {


        let resizeTimer = 0 ;
        $( window ).resize( function () {

            if ( !resourceGraph.getCurrentGraph().is( ':visible' ) )
                return ;
            clearTimeout( resizeTimer ) ;
            resizeTimer = setTimeout( function () {
                console.log( "window Resized" ) ;
                resourceGraph.reDraw() ;
            }, 300 ) ;

        } ) ;

        jQuery( '.numbersOnly' ).keyup( function () {
            this.value = this.value.replace( /[^0-9\.]/g, '' ) ;
        } ) ;

        // refreshButton
        $( ".refreshGraphs", resourceGraph.getCurrentGraph() ).click( function () {
            resourceGraph.settingsUpdated() ;
            return false ; // prevents link
        } ) ;

        $( ".graphTimeZone", resourceGraph.getCurrentGraph() ).change( function () {
            console.log( "graphTimeZone" ) ;
            resourceGraph.reDraw() ;
            return false ; // prevents link
        } ) ;

        $( ".useLineGraph", resourceGraph.getCurrentGraph() ).change( function () {
            console.log( "useLineGraph" ) ;
            resourceGraph.reDraw() ;
            return false ; // prevents link
        } ) ;

        $( ".graphOptions .close-menu", resourceGraph.getCurrentGraph() ).click( function () {
            $( ".graph-display-options", resourceGraph.getCurrentGraph() ).hide() ;
            return false ;
        } )

        $( ".graphOptions .tool-menu", resourceGraph.getCurrentGraph() ).click( function () {
            let $showButton = $( this ) ;
            let $menu = $( ".graph-display-options", resourceGraph.getCurrentGraph() ) ;
            if ( $menu.is( ":visible" ) ) {
                $menu.hide() ;
            } else {
                $menu.show() ;

            }
            return false ;
        } )

    }

    function layoutSetup( resourceGraph ) {

        let $graphContainer = resourceGraph.getCurrentGraph() ;

        $( ".savePreferencesButton", $graphContainer ).click( function () {
            showSavePreferencesDialog( resourceGraph ) ;
        } ).hide() ;

        $( ".layoutSelect", $graphContainer ).selectmenu( {
            width: "10em",
            change: function () {
                layoutChanged( resourceGraph )
            }
        } ) ;
    }

    function layoutChanged( resourceGraph ) {

        let $graphContainer = resourceGraph.getCurrentGraph() ;
        let selectedLayout = $( ".layoutSelect", $graphContainer ).val() ;
        console.log( "layout selected: " + selectedLayout ) ;

        $( ".savePreferencesButton", $graphContainer ).show() ;
        switch ( selectedLayout ) {
            case "spotlight1":
                setGraphsSize( "18%", "15%", $graphContainer, 1 ) ;
                break ;
            case "spotlight2":
                setGraphsSize( "18%", "15%", $graphContainer, 2 ) ;
                break ;
            case "small":
                setGraphsSize( "20%", "15%", $graphContainer ) ;
                break ;
            case "smallWide":
                setGraphsSize( "100%", "15%", $graphContainer ) ;
                break ;
            case "medium":
                setGraphsSize( "30%", "25%", $graphContainer ) ;
                break ;
            case "mediumWide":
                setGraphsSize( "100%", "30%", $graphContainer ) ;
                break ;
        }

        resourceGraph.reDraw() ;
    }

    function addCustomViews( resourceGraph, customViews ) {

        let $graphContainer = resourceGraph.getCurrentGraph() ;
        let $customSelect = $( "select.customViews", $graphContainer ) ;
        jQuery( '<option/>', {
            text: `all`
        } ).appendTo( $customSelect ) ;

        for ( let viewKeys in customViews ) {
            let optionItem = jQuery( '<option/>', {
                value: viewKeys,
                text: viewKeys
            } ) ;
            $customSelect.append( optionItem ) ;
        }
        $( ".customViews", $graphContainer ).selectmenu( {
            width: "10em",
            change: function () {

                let selectedView = $( this ).val() ;
                console.log( "customViews selected: " + selectedView ) ;

                let layouts = "default" ;
                if ( selectedView != "all" )
                    layouts = "mediumWide" ;
                $( ".layoutSelect", $graphContainer ).val( layouts ) ;
                $( ".layoutSelect", $graphContainer ).selectmenu( "refresh" ) ;
                layoutChanged( resourceGraph ) ;
                // _currentResourceGraphInstance.reDraw();
            }
        } ) ;
    }

    function setGraphsSize( width, height, $graphContainer, spotIndex ) {
        // save all sizes
        $( ".plotContainer > div.plotPanel", $graphContainer ).each( function ( index ) {
            let graphName = $( this ).data( "graphname" ) ;

            // console.log("setGraphsSize(): " + spotIndex + " graph: " + graphName + " index: " + index) ;
            let sizeObject = {
                width: width,
                height: height
            } ;

            if ( spotIndex == 1 && index == 0 ) {
                sizeObject.width = "50%" ;
                sizeObject.height = "50%" ;
            }
            if ( spotIndex == 2 && index <= 1 ) {
                sizeObject.width = "45%" ;
                sizeObject.height = "50%" ;
            }
            graphLayout.setSize( graphName, sizeObject, $graphContainer, $( this ).parent() )
        } ) ;
    }

    function showSavePreferencesDialog( resourceGraph ) {
        // 

        if ( !alertify.graphPreferences ) {
            let message = "Saving current settings will enable these to be used on all labs in all lifecycles"
            let startDialogFactory = function factory() {
                return{
                    build: function () {
                        // Move content from template
                        this.setContent( message ) ;
                        this.setting( {
                            'onok': function () {
                                let $graphContainer = resourceGraph.getCurrentGraph() ;

                                let resetResource = false ;
                                if ( $( ".layoutSelect", $graphContainer ).val() == "default" ) {
                                    resetResource = $graphContainer.data( "preference" ) ;
                                }
                                graphLayout.publishPreferences( resetResource ) ;
                            },
                            'oncancel': function () {
                                alertify.warning( "Cancelled Request" ) ;
                            }
                        } ) ;
                    },
                    setup: function () {
                        return {
                            buttons: [ { text: "Save current settings", className: alertify.defaults.theme.ok, key: 0 },
                                { text: "Cancel", className: alertify.defaults.theme.cancel, key: 27/* Esc */ }
                            ],
                            options: {
                                title: "Save Current Layout :", resizable: false, movable: false, maximizable: false,
                            }
                        } ;
                    }

                } ;
            } ;
            alertify.dialog( 'graphPreferences', startDialogFactory, false, 'confirm' ) ;
        }

        alertify.graphPreferences().show() ;
    }

    function zoomSetup( resourceGraph ) {
        let $graphContainer = resourceGraph.getCurrentGraph() ;
        let max = $( "#numSamples > option", $graphContainer ).length ;
        let current = $( "#numSamples", $graphContainer ).prop( "selectedIndex" ) ;

        $( ".zoomSelect", $graphContainer ).empty() ;

        // each instance is copied from the base definition
        for ( let i = 0 ; i < max ; i++ ) {
            let itemInZoom = $( "#numSamples > option:nth-child(" + ( i + 1 ) + ")", $graphContainer ).text() ;
            let sel = "" ;
            if ( i == current ) {
                sel = 'selected="selected"' ;
            }
            $( ".zoomSelect", $graphContainer ).append(
                    "<option " + sel + " >" + itemInZoom + "</option>" ) ;
        }

        let zoomChange = function () {
            console.log( "Zoom changed" ) ;
            $( "#numSamples", $graphContainer ).prop(
                    "selectedIndex",
                    $( ".zoomSelect", $graphContainer ).prop(
                    "selectedIndex" ) ) ;
            console.log( "Zoom changed: " + $( "#numSamples", $graphContainer ).val() ) ;
            // alertify.notify(" Selected: " + $( this ).val())
            if ( $( "#numSamples", $graphContainer ).val() != "99999" ) {
                $( ".sliderContainer", $graphContainer ).show() ;
            } else {
                $( ".useLineGraph", $graphContainer ).prop( "checked", "checked" ) ;

                $( ".sliderContainer", $graphContainer ).hide() ;
            }

            resourceGraph.reDraw() ;
        }

        $( ".zoomSelect", $graphContainer ).selectmenu( {
            width: "6em",
            change: zoomChange
        } ) ;

        $( ".meanFilteringSelect", $graphContainer ).selectmenu( {
            width: "4em",
            change: function () {
                resourceGraph.reDraw() ;
            }
        } ) ;




    }

    function datePickerSetup( resourceGraph ) {

        let $graphContainer = resourceGraph.getCurrentGraph() ;


        let now = new Date() ;

        console.log( `Registering datepicker: local time: ${ now }  Us Central:  ${ csapflot.getUsCentralTime( now )} ` ) ;

        $( ".datepicker", $graphContainer ).datepicker( {
            defaultDate: csapflot.getUsCentralTime( now ),
            maxDate: '0',
            minDate: '-120'
        } ) ;

        $( ".datepicker", $graphContainer ).css( "width", "7em" ) ;

        $( ".datepicker", $graphContainer ).change( function () {
            // $(".daySelect", $GRAPH_INSTANCE).val("...");
            if ( $( ".numDaysSelect", $graphContainer ).val() == 0 )
                $( ".numDaysSelect", $graphContainer ).val( 1 ) ;

            let dayOffset = csapflot.calculateUsCentralDays( $( this ).datepicker( "getDate" ).getTime() ) ;

            console.log( "dayOffset: " + dayOffset ) ;

            $( ".useHistorical", $graphContainer ).prop( 'checked', true ) ;
//            $( ".historicalOptions", $graphContainer ).css(     "display", "inline-block" ) ;

            $( "#dayOffset", $graphContainer ).val( dayOffset ) ;

            resourceGraph.settingsUpdated() ;

            return false ; // prevents link
        } ) ;
    }


    // binds the select
    function numberOfDaysSetup( resourceGraph ) {

        let $graphContainer = resourceGraph.getCurrentGraph() ;

        uiSetupForNumberOfDays( $graphContainer ) ;

        // Handle change events
        $( ".numDaysSelect", $graphContainer ).change(
                function () {
                    let numberOfDaysSelected = $( ".numDaysSelect",
                            $graphContainer ).val() ;

                    console.log( "setupNumberOfDaysChanged(): " + numberOfDaysSelected ) ;

                    if ( numberOfDaysSelected == 0 ) {
                        $( ".useHistorical", $graphContainer ).prop(
                                'checked', false ) ;
                    } else {
                        $( ".useHistorical", $graphContainer ).prop(
                                'checked', true ) ;
                    }

                    // updates the dropDown
                    $( "#numberOfDays", $graphContainer )
                            .val( numberOfDaysSelected ) ;

                    resourceGraph.settingsUpdated() ;

                    return false ; // prevents link
                } ) ;
    }

    function uiSetupForNumberOfDays( $graphContainer ) {
        if ( $( ".numDaysSelect", $graphContainer ).val() == 0 ) {
            $( ".useHistorical", $graphContainer ).prop( 'checked', false ) ;
//            $( ".historicalOptions", $graphContainer ).hide() ;
        } else {
            $( ".useHistorical", $graphContainer ).prop( 'checked', true ) ;
//            $( ".historicalOptions", $graphContainer ).css( "display",  "inline-block" ) ;
        }

        if ( $( ".numDaysSelect", $graphContainer ).length < 5 ) {
            for ( let i = 2 ; i <= 14 ; i++ ) {
                $( ".numDaysSelect", $graphContainer ).append(
                        '<option value="' + i + '" >' + i + " days</option>" ) ;
            }

            $( ".numDaysSelect", $graphContainer ).append(
                    '<option value="21" >3 Weeks</option>' ) ;
            $( ".numDaysSelect", $graphContainer ).append(
                    '<option value="28" >4 Weeks</option>' ) ;
            $( ".numDaysSelect", $graphContainer ).append(
                    '<option value="42" >6 Weeks</option>' ) ;
            $( ".numDaysSelect", $graphContainer ).append(
                    '<option value="56" >8 Weeks</option>' ) ;
            $( ".numDaysSelect", $graphContainer ).append(
                    '<option value="112" >16 Weeks</option>' ) ;
            $( ".numDaysSelect", $graphContainer ).append(
                    '<option value="256" >32 Weeks</option>' ) ;
            $( ".numDaysSelect", $graphContainer ).append(
                    '<option value="336" >48 Weeks</option>' ) ;
            $( ".numDaysSelect", $graphContainer ).append(
                    '<option value="999" >All</option>' ) ;
        }
    }

    function dialogSetup( settingsChangedCallback, $GRAPH_INSTANCE ) {


        // $('.padLatest', $GRAPH_INSTANCE).prop("checked", false) ;

        $( ".resourceConfigDialog .sampleIntervals, .padLatest" ).click(
                function () {
                    $( ".resourceConfigDialog .useAutoInterval" )
                            .prop( 'checked', false ) ;
                } ) ;

        $( '.showSettingsDialogButton', $GRAPH_INSTANCE ).click( function () {

            try {
                dialogShow( settingsChangedCallback, $GRAPH_INSTANCE ) ;
            } catch ( e ) {
                console.log( e ) ;
            }

            setTimeout( function () {
                $( '[title!=""]', "#graphSettingsDialog" ).qtip( {
                    content: {
                        attr: 'title',
                        button: true
                    },
                    style: {
                        classes: 'qtip-bootstrap'
                    }
                } ) ;
            }, "1000" ) ;

            $( ".pointToolTip" ).hide() ;


            return false ; // prevents link
        } ) ;
    }

    /**
     * Static function:  Alertify usage is non-trivial as scope for function
     * is inside ResourceGraph instances, and there are multple instances of Graph.
     * Unlike typical usage, Resource Graph content is pulled in when launched, and moved
     * back to original DOM location in order for 
     */
    function dialogShow( settingsChangedCallback, $GRAPH_INSTANCE ) {

        let dialogId = "graphSettingsDialog" ;

        if ( !alertify.graphSettings ) {

            console.log( "Building: dialogId: " + dialogId ) ;
            let settingsFactory = function factory() {
                return{
                    build: function () {
                        // Move content from template
                        this.setContent( '<div id="' + dialogId + '"></div>' ) ;

                    },
                    setup: function () {
                        return {
                            buttons: [ { text: "Refresh Graphs", className: alertify.defaults.theme.ok, key: 27/* Esc */ } ],
                            options: {
                                title: "Advanced Settings", resizable: true, movable: false, maximizable: false
                            }
                        } ;
                    }

                } ;
            } ;

            alertify.dialog( 'graphSettings', settingsFactory, false, 'alert' ) ;
        }

        let settingsDialog = alertify.graphSettings() ;

        // Settings from associated ResourceGraph moved into dialog
        $( "#" + dialogId ).append( $( ".resourceConfigDialog", $GRAPH_INSTANCE ) ) ;

        settingsDialog.setting( {
            'onclose': function () {
                // Settings moved back to original location
                $( ".resourceConfig", $GRAPH_INSTANCE ).append( $( ".resourceConfigDialog", "#" + dialogId ) ) ;
                settingsChangedCallback() ;
            }
        } ) ;


        let targetWidth = $( window ).outerWidth( true ) - 100 ;
        let targetHeight = $( window ).outerHeight( true ) - 100 ;
        settingsDialog.resizeTo( targetWidth, targetHeight ) ;


        $( ".graph-display-options" ).hide() ;

        settingsDialog.show() ;
    }

    function modifyTimeSlider( $newGraphContainer, sampleTimeArray, descTimeArray, resourceGraph ) {

        if ( sampleTimeArray.length <= 0 ) {

            alertify
                    .alert( "No data available in selected range. Select another range or try again later." ) ;
            return ;
        }
        let maxItems = sampleTimeArray.length - 1 ;

        let d = new Date( parseInt( descTimeArray[maxItems] ) ) ;
        let mins = d.getMinutes() ;
        if ( mins <= 9 )
            mins = "0" + mins ;
        let formatedDate = d.getHours() + ":" + mins + " "
                + $.datepicker.formatDate( 'M d', d ) ;

        $( ".sliderTimeStart", $newGraphContainer ).val( formatedDate ) ;

        // alert (maxItems + " timerArray[0]: " + timerArray[0] + "
        // timerArray[maxItems]:" + timerArray[maxItems]) ;
        // alert (new Date(parseInt(sampleTimeArray[0]))) ;

        let hostAuto = $( ".autoRefresh", $newGraphContainer ) ;

        let minSlider = 0 ;
        let numSamples = $( "#numSamples", resourceGraph.getCurrentGraph() ).val() ;
        if ( sampleTimeArray.length > numSamples ) {
            minSlider = numSamples - 5 ;
        } else if ( sampleTimeArray.length > 10 ) {
            minSlider = 10 ;
        }

        let sliderConfig = {
            value: maxItems,
            min: minSlider,
            max: maxItems,
            step: 1,
            slide: function ( event, ui ) {
//					 setSliderLabel( $newGraphContainer,
//								descTimeArray[maxItems - ui.value] );

                resourceGraph.clearRefreshTimers() ;
                sliderUpdatePosition( $slider, ui.value, resourceGraph, sampleTimeArray, descTimeArray ) ;
            },
            stop: function ( event, ui ) {
                resourceGraph.clearRefreshTimers() ;
                sliderUpdatePosition( $slider, ui.value, resourceGraph, sampleTimeArray, descTimeArray ) ;
            }
        }


        let $slider = $( ".resourceSlider", $newGraphContainer ).slider( sliderConfig ) ;


        $( ".playTimelineButton", $newGraphContainer ).off().click( function () {

            let $buttonImage = $( "img", $( this ) ) ;
            resourceGraph.clearRefreshTimers() ;

            if ( $buttonImage.attr( "src" ) == startButtonUrl ) {
                isKeepPlaying = false ;
                $buttonImage.attr( "src", pauseButtonUrl ) ;
                let currentLocation = $slider.slider( "value" ) ;
                ;
                if ( currentLocation > ( maxItems - 10 ) )
                    $slider.slider( "value", 0 ) ; // restart or resume
                setTimeout( function () {
                    isKeepPlaying = true ;
                    playSlider( 1, $buttonImage, $slider, resourceGraph, sampleTimeArray, descTimeArray ) ;
                }, 500 )
            } else {
                isKeepPlaying = false ;
                $buttonImage.attr( "src", startButtonUrl ) ;
            }


        } ) ;
        $( ".playTimelineBackButton", $newGraphContainer ).off().click( function () {

            let $buttonImage = $( "img", $( this ) ) ;
            resourceGraph.clearRefreshTimers() ;

            if ( $buttonImage.attr( "src" ) == startButtonUrl ) {
                isKeepPlaying = false ;
                $buttonImage.attr( "src", pauseButtonUrl ) ;
                let currentLocation = $slider.slider( "value" ) ;
                let zoomSetting = $( "#numSamples", resourceGraph.getCurrentGraph() ).val() ;
                if ( ( currentLocation - zoomSetting ) <= 10 )
                    $slider.slider( "value", maxItems ) ; // restart or resume
                setTimeout( function () {
                    isKeepPlaying = true ;
                    playSlider( -1, $buttonImage, $slider, resourceGraph, sampleTimeArray, descTimeArray ) ;
                }, 500 )
            } else {
                isKeepPlaying = false ;
                $buttonImage.attr( "src", startButtonUrl ) ;
            }


        } ) ;

        sliderUpdatePosition( $slider, maxItems, resourceGraph, sampleTimeArray, descTimeArray ) ;
    }

    function playSlider( offset, $buttonImage, $slider, resourceGraph, sampleTimeArray, descTimeArray ) {

        let $graphContainer = resourceGraph.getCurrentGraph() ;
        let newPosition = $slider.slider( "value" ) + offset ;

        let zoomSetting = $( "#numSamples", $graphContainer ).val() ;
//		  console.log( "Starting to play: " + newPosition + " max:" + descTimeArray.length
//					 + " samples: " + zoomSetting );

        if ( offset > 0 && newPosition >= descTimeArray.length )
            isKeepPlaying = false ;
        if ( offset < 0 && ( newPosition - zoomSetting ) <= 0 )
            isKeepPlaying = false ;
        if ( !isKeepPlaying ) {
            $buttonImage.attr( "src", startButtonUrl ) ;
            return ;
        }


        sliderUpdatePosition( $slider, newPosition, resourceGraph, sampleTimeArray, descTimeArray ) ;

        // do it again
        let delay = 5000 / zoomSetting ;

        setTimeout( function () {
            playSlider( offset, $buttonImage, $slider, resourceGraph, sampleTimeArray, descTimeArray ) ;
        }, delay ) ;

    }

    function sliderUpdatePosition( $slider, position, resourceGraph, sampleTimeArray, descTimeArray ) {

        let reversePosition = sampleTimeArray.length - position ;
        // set the label

        setSliderLabel( resourceGraph.getCurrentGraph(), descTimeArray[ reversePosition ] ) ;

        // move the slider
        $slider.slider( "value", position ) ;
        let host = $slider.parent().parent().parent().data( "host" ) ;


        // console.log( `sliderUpdatePosition() host: ${ host },  reversePosition: ${ reversePosition }` ) ;

        // console.log("host: " + host ) ;
        // move the grapsh 
        let graphsArray = resourceGraph.getDataManager().getHostGraph( host ) ;

        //console.log("Slider Modified: updating graphs: ${ graphsArray.length } ") ;

        if ( graphsArray == undefined ) {
            return ; // initial rendering
        }

        for ( let plot of graphsArray ) {
            // Custom Hook into FLOT nav plugin.
           
            if ( plot.jumpX ) {
//                 console.log( `jumpX enabled into plot` ) ;
                plot.jumpX( {
                    // sample times are in reverse order
                    x: parseInt( sampleTimeArray[ reversePosition ] )
                } ) ;
            } else { 
                console.log( `jumpX disabled` ) ;
            }
        }
    }

    function setSliderLabel( $newGraphContainer, newTime ) {

        let d = new Date( parseInt( newTime ) ) ;
        let mins = d.getMinutes() ;
        if ( mins <= 9 )
            mins = "0" + mins ;
        let formatedDate = d.getHours() + ":" + mins + " "
                + $.datepicker.formatDate( 'M d', d ) ;

        // alert( formatedDate) ;
        $( ".sliderTimeCurrent", $newGraphContainer ).val( formatedDate ) ;

    }

    function addToolsEvents() {

        $( '.hostLaunch' ).click(
                function () {


                    let linkHost = $( this ).parent().parent().parent().data( "host" ) ;
                    let baseUrl = agentHostUrlPattern.replace( /CSAP_HOST/g, linkHost ) ;
                    let theUrl = baseUrl + "/app-browser?u=1" ;
                    openWindowSafely( theUrl, linkHost + "Stats" ) ;

                    return false ; // prevents link
                } ) ;
    }

    function addContainerEvents( resourceGraph, container ) {
        if ( resourceGraph.isCurrentModePerformancePortal() ) {
            $( '.clearMetrics', container ).hide() ;
            $( ".autoRefresh" ).hide() ;
        }
        $( '.clearMetrics', container ).click(
                function () {

                    let linkHost = $( this ).parent().parent()
                            .parent().data( "host" ) ;

                    let message = "Clear metrics only clears the UI history. Reloading the page will display entire timeline. Host: "
                            + linkHost ;
                    alertify.notify( message ) ;

                    resourceGraph.getDataManager().clearHostData( linkHost ) ;
                    //  _metricsJsonCache[linkHost] = undefined;

                    setTimeout( function () {
                        resourceGraph.getMetrics( 2, linkHost ) ;
                    }, "1000" ) ;

                    return false ; // prevents link
                } ) ;

        let hostAuto = $( ".autoRefresh", container ) ;
        hostAuto.change( function () {
            // console.log( "hostAuto.is(':checked')" + hostAuto.is( ':checked' ) );
            if ( hostAuto.is( ':checked' ) ) {
                resourceGraph.getMetrics( 1, container.data( "host" ) ) ;
            } else {
                // alert("Clearing timer" + newHostContainerJQ.data("host")
                // ) ;
                resourceGraph.clearRefreshTimers( container.data( "host" ) ) ;
            }
            return false ; // prevents link
        } ) ;
    }

    function postDrawEvents( $newGraphContainer, $GRAPH_INSTANCE, numDays ) {
        let d = new Date() ;

        // ugly - but prefix single digits with a 0 consistent with times
        let curr_hour = ( "0" + d.getHours() ).slice( -2 ) ;
        let curr_min = ( "0" + d.getMinutes() ).slice( -2 ) ;
        let curr_sec = ( "0" + d.getSeconds() ).slice( -2 ) ;
        $( ".refresh", $newGraphContainer ).html(
                "refreshed: " + curr_hour + ":" + curr_min + ":" + curr_sec ) ;

        $( '.useHistorical', $GRAPH_INSTANCE ).off( 'change' ).change(
                function () {
                    // console.log("Toggling Historical") ;
                    $( '.historicalContainer', $GRAPH_INSTANCE ).toggle() ;
                } ) ;


        // Finally Update the calendar based on available days
        $( ".datepicker", $GRAPH_INSTANCE ).datepicker( "option", "minDate",
                1 - numDays ) ;


    }
} ) ;