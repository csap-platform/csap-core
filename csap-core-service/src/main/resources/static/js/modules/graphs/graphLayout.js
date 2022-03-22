// define( [ ], function () {

//     console.log( "Module loaded: graphPackage/graphLayout" ) ;


import _dom from "../utils/dom-utils.js";

const graphLayout = graphs_layouts();

export default graphLayout


function graphs_layouts() {

    _dom.logHead( "Module loaded" );

    var _graphSettings = null;

    //	 var graphCookieName = "csapGraphs_Demo";
    //	 var grapCookieExpireDays = 365;

    var _customGraphSizes = new Object();

    return {
        //
        addContainer: function ( baseContainerId, $plotContainer ) {
            addContainer( baseContainerId, $plotContainer );
        },
        publishPreferences: function ( resetResource ) {
            publishPreferences( resetResource );
        },
        //
        restore: function ( resourceGraph, $hostPlotContainer ) {
            restore( resourceGraph, $hostPlotContainer );
        },
        isAGraphMaximized: function ( $graphContainer ) {
            return isAGraphMaximized( $graphContainer )
        },
        //
        getWidth: function ( graphName, $graphContainer ) {

            var size = "50%"
            if ( $( ".layoutSelect", $graphContainer ).val() != "default"
                && _customGraphSizes[ graphName ] != undefined ) {
                size = _customGraphSizes[ graphName ].width;
            }
            if ( isAGraphMaximized( $graphContainer ) ) {
                size = "100%";
            }
            console.log( "getWidth(): " + graphName + " : " + size + " layout: "
                + $( ".layoutSelect", $graphContainer ).val() +
                " container: " +
                $graphContainer.attr( "id" ) );

            return size;
        },
        //
        getHeight: function ( graphName, $graphContainer ) {

            var size = "200"
            if ( $( ".layoutSelect", $graphContainer ).val() != "default"
                && _customGraphSizes[ graphName ] != undefined ) {
                size = _customGraphSizes[ graphName ].height;
            }
            if ( isAGraphMaximized( $graphContainer ) ) {
                size = "100%";
            }
            //console.log("getHeight(): " + graphName + " : "+ size) ;
            return size;
        },
        setSize: function ( graphName, size, $graphContainer, $plotContainer ) {
            console.log( "Updating size" + graphName + " width" + size.width );
            _customGraphSizes[ graphName ] = size;
            saveLayout( $graphContainer, $plotContainer );
        }
    };

    function isAGraphMaximized( $graphContainer ) {
        // console.log("isAGraphMaximized() : " + $( ".graphCheckboxes :checked", $graphContainer ).length )
        return $( ".graphCheckboxes :checked", $graphContainer ).length == 1;
    }

    function addContainer( $graphContainer, $plotContainer ) {
        $plotContainer.sortable( {
            handle: '.graphTitle',
            update: function ( event, ui ) {
                console.log( "panel moved: " + ui.helper );
                saveLayout( $graphContainer, $plotContainer );
            }
        } );

        $plotContainer.on( 'resize', function ( e ) {
            // otherwise resize window will be continuosly called
            e.stopPropagation();
        } );
    }

    function loadPreferences() {

        _graphSettings = new Object();
        var paramObject = {
            "dummy": jsonToString( _graphSettings )
        }
        $.ajax( {
            dataType: "json",
            url: OS_URL + "/user/settings",
            //async: false,
            data: paramObject,
            success: function ( responseJson ) {
                // console.log( "loadPreferences():  " + jsonToString( responseJson ) );
                if ( responseJson && responseJson.response != undefined ) {
                    _graphSettings = responseJson.response;
                } else {
                    //  alertify.warning( "Warning: failed to load user preferencs " )
                    console.log( "loadPreferences(): User preferences not found" )
                }
            }
        } );

    }
    // push to csap event service for loading in other labs
    function publishPreferences( resetResource ) {

        if ( resetResource != false ) {
            delete _graphSettings[ resetResource ];
        }
        var paramObject = {
            "new": jsonToString( _graphSettings )
        }

        $.ajax( {
            method: "post",
            dataType: "json",
            url: OS_URL + "/user/settings",
            async: true,
            data: paramObject,
            success: function ( responseJson ) {
                alertify.notify( "Default view stored, and will be used in all Applications",
                    "success", 1 );
            },
            error: function ( jqXHR, textStatus, errorThrown ) {

                // handleConnectionError("Performance Intervals", errorThrown);
                // console.log("Performance Intervals" + JSON.stringify(jqXHR, null, "\t")) ;
                handleConnectionError( "settingsUpdate", errorThrown );
            }
        } );


    }

    function saveLayout( $graphContainer, $plotContainer ) {

        layoutSelectCheck( "Current*", $graphContainer );
        $( ".savePreferencesButton", $graphContainer ).show();
        $( ".layoutSelect", $graphContainer ).val( "Current*" ).selectmenu( "refresh" );

        var allGraphs = new Array();

        $( ">div.plotPanel", $plotContainer ).each( function ( index ) {
            // console.log( "FOund: " + $( this ).data( "graphname" ) );
            var graphDetails = new Object();
            graphDetails.name = $( this ).data( "graphname" );
            graphDetails.size = _customGraphSizes[ graphDetails.name ];
            allGraphs.push( graphDetails );
        } );

        _graphSettings[ $graphContainer.data( "preference" ) ] = allGraphs;

    }

    function restore( resourceGraph, $hostPlotContainer ) {

        if ( _graphSettings == null ) {

            if ( window.csapGraphSettings != undefined ) {
                console.log( "loadPreferences() skipping window.csapGraphSettings" );
                _graphSettings = {};
            } else {
                loadPreferences();
            }
        }

        var $rootContainer = $hostPlotContainer.parent().parent().parent();

        var baseContainerId = $rootContainer.data( "preference" );
        //console.log("baseContainerId: " + baseContainerId) ;

        if ( _graphSettings[ baseContainerId ] != null )
            layoutSelectCheck( "My Layout", $rootContainer );

        if ( $( ".layoutSelect", $rootContainer ).val() == "default" )
            return;

        // customized view support for JMX, possible others.
        var customView = resourceGraph.getSelectedCustomView();
        if ( customView != null ) {
            $( "> div.plotPanel", $hostPlotContainer ).each( function ( index ) {
                var graphName = $( this ).data( "graphname" );
                //console.log("graphName: "+ graphName );
                if ( $.inArray( graphName, customView.graphs ) == -1 ) {
                    $( this ).hide();
                }

            } );

            var customGraphOrder = customView.graphs;
            for ( var i = 0; i < customGraphOrder.length; i++ ) {
                var graphName = customGraphOrder[ i ]
                var $targetGraph = $( '>div.' + graphName, $hostPlotContainer );
                $hostPlotContainer.append( $targetGraph );

                if ( customView.graphSize != undefined &&
                    customView.graphSize[ graphName ] != undefined ) {
                    _customGraphSizes[ graphName ] = customView.graphSize[ graphName ];
                }
            }
            return;
        }

        if ( _graphSettings[ baseContainerId ] == null )
            return;

        var graphs = _graphSettings[ baseContainerId ];

        var $plotContainer = $( ".ui-sortable", $rootContainer );
        console.log( "baseContainerId: " + baseContainerId
            + " graphs.length: " + graphs.length
            + " Number of Containers: " + $plotContainer.length )

        for ( var i = 0; i < graphs.length; i++ ) {

            var graphDetails = graphs[ i ];

            // console.log("Restoring: " + graphDetails.name) ;
            var $targetGraph = $( '>div.' + graphDetails.name, $hostPlotContainer );

            //	console.log( " $targetGraph: " + $targetGraph.attr( "id" ) + "  $plotContainer: " + $plotContainer.prop( 'className' ) )
            // $targetGraph.appendTo( $plotContainer );
            // do the move.
            $hostPlotContainer.append( $targetGraph );
            if ( graphDetails.size != undefined ) {
                _customGraphSizes[ graphDetails.name ] = graphDetails.size;
            }
        }
    }

    function layoutSelectCheck( layoutName, $graphContainer ) {
        if ( $( ".layoutSelect option[value='" + layoutName + "']", $graphContainer ).length == 0 ) {
            var customOption = jQuery( '<option/>', {
                text: layoutName,
                value: layoutName
            } );

            $( ".layoutSelect", $graphContainer ).append( customOption );
            $( ".layoutSelect", $graphContainer ).val( layoutName ).selectmenu( "refresh" );
        }

    }

}