

import _dom from "../utils/dom-utils.js";

import model from "./model.js";
import tabs from "./tabs.js";


const hostSelectForGraphs = host_select_for_graphs();

export default hostSelectForGraphs


function host_select_for_graphs() {

    _dom.logHead( "Module loaded2" );



    var $hostSelect = $( "#vmSelectAttribute" );

    return {
        // 
        initializeSmartSelect: function () {
            initialize();
        },
        updateHosts: function () {
            updateHostsInSelectDropDown();
        }
    }

    function initialize() {
        console.log( "Starting initialization" );

        // main ui event binding
        $( '#hostCustomize' ).click( function () {

            customizeDialogForCurrentGraph();
            showDialog();

        } );


        $( '#isStackHosts' ).change( function () {

            tabs.reInitGraphs();

        } );


        // Dialog Event binding
        $( '#hostUnCheckAll' ).click( function () {

            $( 'input', "#hostDisplay" ).prop( "checked", false ).trigger( "change" );
            return false; // prevents link
        } );

        $( '#hostCheckAll' ).click( function () {
            $( 'input', "#hostDisplay" ).prop( "checked", true ).trigger( "change" );
        } );


        console.log( "Converting host select to selectmenu, matched elements:", $hostSelect.length );
        $hostSelect.selectmenu( {
            width: "9em",
            // position: { my : "right+45 top+12", at: "bottom right" },
            change: function () {
                getHostToSelect( "top" );
                getHostToSelect( "low" );
                $( "#vmSelectText" ).text( "Selected by:  " + $( '#vmSelectAttribute option:selected' ).text() );
                $hostSelect.val( 0 );
                $hostSelect.selectmenu( "refresh" );
            }

        } );


    }


    // only used when displaying 
    function updateHostsInSelectDropDown() {

        $( '#hostSelection input' ).unbind( 'click' );

        var lcSelected = $( '#lcSelect' ).val();
        // if ( lcSelected == "none") return false;
        // alert (lcSelected) ;
        $( "#hostDisplay" ).empty();

        console.log( "updateHosts() lcSelected: " + lcSelected );
        var clusterDef = model.getPackageDetails( model.getSelectedProject() ).clusters;
        if ( clusterDef == undefined ) {
            alertify.alert( "Selected project/lifecycle does not have any hosts. Try another." );
            return;
        }
        var currCluster = clusterDef[ lcSelected ];
        // console.log("updateHosts() cluster: " + jsonToString( currCluster )) ;

        for ( var i = 0; i < currCluster.length; i++ ) {
            var host = currCluster[ i ];
            var id = host + "Check";
            var checkedAttribute = "";
            if ( uiSettings.hostParam != null && uiSettings.hostParam.indexOf( host ) != -1 )
                checkedAttribute = ' checked="checked" ';
            $( "#hostDisplay" )
                .append(
                    '<div class="hostCustom">'
                    + '<input id="'
                    + id
                    + '" style="margin-right: 0.2em" data-host="'
                    + host
                    + '"'
                    + checkedAttribute
                    + ' class="instanceCheck" type="checkbox" title="Select to include in operations"/>'
                    + '<label for="' + id
                    + '" class="hostLabel">'
                    + host + '</label></div>' );
        }
    }

    function getReportAttributes( reportId ) {

        var reportUrl = uiSettings.metricsDataUrl + "../report/" + reportId;
        var paramObject = {
            appId: $( "#appIdFilterSelect" ).val(),
            project: model.getSelectedProject(),
            life: $( "#lifeSelect" ).val(),
            numDays: 1
        };

        $.getJSON(
            reportUrl,
            paramObject )

            .done( function ( responseJson ) {
                //reportServiceSuccess( responseJson, numDays, report );
                //console.log( "getReportAttributes(): ", responseJson );


                if ( reportId == "vm" && responseJson.data ) {
                    var foundFieldCount = 0;
                    for ( var i = 0; i < responseJson.data.length; i++ ) {
                        var hostSummary = responseJson.data[ i ];
                        console.log( "hostSummary(): ", hostSummary );
                        for ( var field in hostSummary ) {
                            var label = model.getServiceLabels( null, field );
                            var $option = jQuery( '<option/>', {
                                value: "vm." + field,
                                text: label
                            } );
                            $hostSelect.append( $option );

                            if ( field == "fdTotal" && hostSummary[ field ] > 10 ) {
                                foundFieldCount++;
                            }
                        }
                        console.log( "foundFieldCount", foundFieldCount )

                        // at least 2 hosts will be checked
                        if ( foundFieldCount >= 2 ) {
                            break;
                        }
                    }
                }

            } )

            .fail( function ( jqXHR, textStatus, errorThrown ) {
                //reportSuccess( null );
                console.error( "Error: Retrieving lifeCycleSuccess fpr host " + hostName, errorThrown )
                // handleConnectionError( "Retrieving lifeCycleSuccess fpr host " + hostName , errorThrown ) ;
            } );
    }

    // 
    function customizeDialogForCurrentGraph() {
        var graphContainer = $( ".graphsContainer:visible" ).attr( "id" );
        console.log( "customizeDialogForCurrentGraph()", graphContainer );

        $hostSelect.empty();

        if ( graphContainer == "resourceGraphDiv" ) {
            // ideally - this is datafilled from reports,for now : hardcoded
            getReportAttributes( "vm" );

        }


        // Add additional selection criteria based on which graph is being displayed
        if ( $( ".triggerJmxCustom" ).length == 1 && $( ".triggerJmxCustom" ).text() != "" ) {
            $( '.graphCheckboxes input' ).each( function () {

                var svcName = $( "input.servicenameCheck:checked" ).first().data( "servicename" );
                var metricIdentifier = "jmxCustom." + $( ".triggerJmxCustom" ).text() + "." + $( this ).attr( "value" )
                var label = model.getServiceLabels( svcName, $( this ).attr( "value" ) );
                var $option = jQuery( '<option/>', {
                    value: metricIdentifier,
                    text: svcName + " - " + label
                } );
                $hostSelect.append( $option );
                console.log( "Adding app option: " + $option.text() );

            } );

        } else {
            // JMX or OS Service - or HOST specific
            var reportPrefix, graphLabelPrefix, reportSuffix;

            switch ( graphContainer ) {

                case "hostGraphDiv":
                    // not possible because of the above condition.
                    // ideally - this is datafilled from reports, not using graph
                    reportPrefix = "host.";
                    reportSuffix = "";
                    graphLabelPrefix = "Host";
                    //return;
                    break;

                case "javaGraphDiv":
                    reportPrefix = "jmx.";
                    graphLabelPrefix = $( "input.servicenameCheck:checked" ).first().data( "servicename" );
                    reportSuffix = "_" + graphLabelPrefix;
                    break;

                case "os-processGraphDiv":
                    reportPrefix = "process.";
                    graphLabelPrefix = $( "input.servicenameCheck:checked" ).first().data( "servicename" );
                    reportSuffix = "_" + graphLabelPrefix;
                    break;

                default:
                    console.error( "unexpected graph type:", graphContainer );
                    break;

            }

            console.log( "\n\n===== container: ", graphContainer, " prefix: ", graphLabelPrefix,
                " report: ", reportPrefix, " reportSuffix", reportSuffix );
            $( '.graphCheckboxes input' ).each( function () {

                var settingsAttribute = $( this ).attr( "value" );
                // backwards compatiblity: attribute was inferred from graphs, which were tweak for 
                switch ( settingsAttribute ) {
                    case "Cpu_15s":
                        settingsAttribute = "topCpu";
                        break;

                    case "diskUtilInMB":
                        settingsAttribute = "diskUtil";
                        break;

                    case "rssMemoryInMB":
                        settingsAttribute = "rssMemory";
                        break;
                }

                if ( settingsAttribute.indexOf( "Cpu_As_Reported_By_JVM" ) == 0 ) {
                    settingsAttribute = "cpuPercent";
                }
                /**
                 * the above can be deleted in 6.x
                 */
                var metricIdentifier = reportPrefix + settingsAttribute + reportSuffix;
                var label = model.getServiceLabels( graphLabelPrefix, settingsAttribute );

                var $option = jQuery( '<option/>', {
                    value: metricIdentifier,
                    text: graphLabelPrefix + " - " + label
                } );
                $hostSelect.append( $option );
                console.log( "Adding java option: " + $option.text() );

            } );

        }
        $hostSelect.sortSelect();

        // compute attributes are always available in selection
        $( 'option', "#vmSelectTemplate" ).each( function () {
            var $option = jQuery( '<option/>', {
                value: $( this ).attr( "value" ),
                text: $( this ).text()
            } );
            $hostSelect.prepend( $option );
            console.log( "Adding host option: " + $option.text() );

        } );
        $hostSelect.val( 0 );
        $hostSelect.selectmenu( "refresh" );
    }

    function showDialog() {
        if ( !alertify.hostSelect ) {
            // lazy construction of dialog
            alertify.dialog( 'hostSelect', alertifyFactory, false, 'alert' );
        }

        // this displays
        alertify.hostSelect().show();

        $( ".hostLabel", $( "input.instanceCheck:checked" ).parent() )
            .animate( {
                "background-color": "yellow"
            }, 1000 ).fadeOut( "fast" ).fadeIn( "fast" );

        $( "input.instanceCheck" ).change( function () {
            var highlightColor = $( ".ajs-dialog" ).css( "background-color" );

            if ( $( this ).is( ":checked" ) )
                highlightColor = "yellow";

            // $(".hostLabel", $("input.instanceCheck").parent()).css("background-color", $(".ajs-dialog").css("background-color") ) ;
            $( ".hostLabel", $( this ).parent() ).css( "background-color", highlightColor );
        } );
    }


    function alertifyFactory() {
        return {
            build: function () {
                // Move content from template
                this.setContent( $( "#hostCusomizeDialog" ).show()[ 0 ] );
                this.setting( {
                    'onok': function () {
                        //  alertify.notify( "Closing Windows" );
                        tabs.updateHostLabels();
                        tabs.reInitGraphs();
                    }
                } );
            },
            setup: function () {
                return {
                    buttons: [ { text: "Refresh Graphs", className: alertify.defaults.theme.ok }
                    ],
                    options: {
                        title: 'Host Selection: <span id="vmSelectText"></span>',
                        resizable: false, movable: false, maximizable: false,
                    }
                };
            }

        };
    }


    function getHostToSelect( type ) {
        $( 'body' ).css( 'cursor', 'wait' );
        if ( $( "#" + type + "VmSelect" ).val() == 0 )
            return;



        // var dayOffset = $("#dayOffset", resourceRootContainer).val();

        var dayOffset = $( "#dayOffset", ".graphsContainer:visible" ).val();

        var metricsId = $hostSelect.val();

        var paramObject = {
            metricsId: metricsId,
            hosts: $( "#" + type + "VmSelect" ).val(),
            numDays: $( "#numReportDays" ).val(),
            dateOffSet: dayOffset,
            project: model.getSelectedProject(),
            life: $( "#lifeSelect" ).val(),
            appId: $( "#appIdFilterSelect" ).val()
        };

        $.getJSON( uiSettings.metricsDataUrl + "../report/" + type,
            paramObject )

            .done( function ( responseJson ) {
                getHostToSelectSuccess( responseJson );
                $( 'body' ).css( 'cursor', 'default' );

            } )

            .fail(
                function ( jqXHR, textStatus, errorThrown ) {

                    handleConnectionError( "Retrieving lifeCycleSuccess fpr host "
                        + hostName, errorThrown );
                    $( 'body' ).css( 'cursor', 'default' );
                } );

    }

    function getHostToSelectSuccess( responseJson ) {

        if ( responseJson == null ) {
            alertify.alert( "Failed to get response , refresh the graphs" );
            return;
        }

        var skippedHosts = "";
        for ( var i = 0; i < responseJson.length; i++ ) {

            var hostName = responseJson[ i ];

            if ( $( "#" + hostName + "Check" ).length == 0 ) {
                skippedHosts += " " + hostName;
                continue;
            }
            $( "#" + hostName + "Check" )
                .prop( 'checked', true )
            console.log( " Selected: " + hostName );
        }

        if ( skippedHosts.length != 0 )
            alertify.notify( "The following host were skipped because they are not in selected cluster " + skippedHosts );

        $( ".hostLabel", $( "input.instanceCheck" ).parent() ).css( "background-color", $( ".ajs-dialog" ).css( "background-color" ) );


        $( ".hostLabel", $( "input.instanceCheck:checked" ).parent() )
            .animate( {
                "background-color": "yellow"
            }, 1000 ).fadeOut( "fast" ).fadeIn( "fast" );


    }

}

