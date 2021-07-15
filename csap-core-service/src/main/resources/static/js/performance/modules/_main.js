

// http://requirejs.org/docs/api.html#packages
// Packages are not quite as ez as they appear, review the above
require.config( {
    paths: {
        ace: BASE_URL + "webjars/ace-builds/1.4.11/src",
        jsYaml: BASE_URL + "webjars/js-yaml/3.14.0/dist",
        
        // CRITICAL: use relative paths to leverage spring boot VersionResourceResolver
        browser:  "../../app-browser/modules",
        editor: "../../editor/modules",
        mathjs: "../../csapLibs/mathjs/modules/math.min",
        csapflot: "../../csapLibs/modules/csap-flot-helpers"
    },
    shim: {
        csapflot: {
            exports: 'csapflot',
            init: function () {
                return {
                    getUsCentralTime: getUsCentralTime,
                    calculateUsCentralDays: calculateUsCentralDays
                } ;
            }
        }
    },
    packages: [
        { name: 'reportPackage',
            location: 'report', // default 'packagename'
            main: '_reportMain'                // default 'main' 
        },
        { name: 'graphPackage',
            location: '../../graphs/modules/graphPackage', // default 'packagename'
            main: 'ResourceGraph'                // default 'main' 
        }
    ]
} ) ;



require( [ "browser/utils", "mathjs", "hostSelectForGraphs", "tabs", "reportPackage", "header", "model" ], function ( utils, mathjs, hostDialog, tabs, reportPackage, header, model ) {

    console.log( "\n\n ************** _main: loaded *** \n\n" ) ;

    // Shared variables need to be visible prior to scope

    $( document ).ready( function () {
        initialize() ;
    } ) ;


    function initialize() {

        $.jqplot.config.enablePlugins = true ;

        tabs.registerTabChanges() ;

        $( "#showAllServicesButton" ).click( function () {
            reportPackage.showAllServices() ;
        } ) ;

        $( "#computeDiv select, #vmSummary input" ).change( function () {
            reportPackage.runSummaryReports() ;
        } ) ;

        // Default to use historical in case target VM is down
        $( ".useHistorical" ).prop( 'checked', true ) ;

        // $('.daySelect option[value="-1"]').remove();
        // $(".daySelect").prop("selectedIndex", 1) ;
        $( '.numDaysSelect option[value="0"]' ).remove() ;
        $( ".legendOptions .datepicker" ).attr( "placeholder", "Last 24 Hours" ) ;
        // $(".numDaysSelect", resourceRootContainer).val(1) ;

        $( '.historicalContainer' ).show() ;

        $( '#showAllColsButton' ).click(
                function () {
                    var isChecked = $( '.columnSelector:visible input:first' ).prop(
                            "checked" ) ;
                    // console.log("isChecked: " + isChecked) ;
                    $( '.columnSelector:visible input' ).prop( "checked", !isChecked ) ;
                    $( '.columnSelector:visible input:first' ).prop( "checked",
                            isChecked ) ;
                    $( '.columnSelector:visible input:first' ).click() ;
                } ) ;

        $( '#generateEmailButton' ).off() ;
        $( '#generateEmailButton' ).click( function () {
            showEmailDialog() ;
            return false ; // prevents link
        } ) ;


        $( "#clearCompareButton" ).hide() ;
        $( "#clearCompareButton" ).click( function () {
            $( "#clearCompareButton" ).hide() ;
            $( "#compareStartInput" ).val( "" ) ;
            reportPackage.triggerReport( $( "#compareStartInput" ) ) ;
            return false ; // prevents link
        } ) ;

        $( "#visualizeSelect" ).change( function () {
        	$( "#compareStartInput" ).attr( "placeholder", "none" ) ;
        	reportPackage.triggerReport( $( "#visualizeSelect" ) ) ;
        }) ;
//        $( "#visualizeSelect" ).selectmenu( {
//            width: "18em",
//            change: function () {
//                $( "#compareStartInput" ).attr( "placeholder", "none" ) ;
//
//                reportPackage.triggerReport( $( "#visualizeSelect" ) ) ;
//
//            }
//        } ) ;



        $( "#isUseDailyTotal" ).change( function () {
            if ( $( "#isUseDailyTotal" ).is( ':checked' ) ) {
                $( "#nomalizeContainer" ).hide() ;
            } else {
                $( "#nomalizeContainer" ).show() ;
            }
        } ) ;


        var reportSettingIds = "#nomalizeContainer select, #isUseDailyTotal, #isUseVmTotal, #isTrendAll,"
                + "#metricsTrendingSelect, #compareThreshold, #clusterSelect, #histogramSort,"
                + "#numReportDays, #reportOptions input" ;

        $( reportSettingIds ).change(
                function () {
                    model.updateClusterServices( ) ;
                    reportPackage.triggerReport( $( this ) ) ;
                } ) ;

        $( '#idMetricSelect' ).change( function () {
            tabs.reInitMetricGraphs() ;
        } ) ;

        $( '#dayMetricSelect' ).change( function () {
            getMetricsListing() ;
        } ) ;

        // getHosts();
        // $(document).tooltip();
//        $( '[data-qtip!=""]' ).qtip( {
//            content: {
//                attr: 'data-qtip'
//            },
//            style: {
//                classes: 'qtip-bootstrap'
//            },
//            position: {
//                my: 'top left', // Position my top left...
//                at: 'bottom right'
//            }
//        } ) ;

        initializeTablesWithSorting() ;

        header.initialize() ;
        reportPackage.initialize() ;
        
        
        // if ( $("#appIdFilterSelect").val() == "none") {
        // alertify.prompt("Select an appId to get started") ;
        // return ;
        // }

        if ( uiSettings.appIdParam != "null" && uiSettings.lifeParam != "null" ) {
            model.updateLifecycleModel() ;

            if ( uiSettings.reportRequest == "tableHost" ) {
                reportPackage.getReport( METRICS_HOST ) ;
                tabs.activateTab( "tableHost" ) ;
            } else {
                if ( uiSettings.reportRequest.indexOf( "graph" ) == 0 ) {
                    // console.log("We need lifecucle info first") ;
                } else if ( uiSettings.reportRequest.indexOf( METRICS_JAVA ) == 0
                        || uiSettings.reportRequest.indexOf( METRICS_OS_PROCESS ) == 0
                        || uiSettings.reportRequest.indexOf( METRICS_APPLICATION ) == 0 ) {

                    $.when( model.getModelLoadedDeffered() ).done( function () {
                        tabs.activateTab( "tableService" ) ;
                    } ) ;
                } else {
                    tabs.activateTab( uiSettings.reportRequest ) ;
                }
            }
        } 
        utils.loadingComplete() ;

        $( "input.datepicker" ).attr( "placeholder", "today" )

        hostDialog.initializeSmartSelect() ;

        $( '#reportCustomizeButton' ).click( showSettingsDialog ) ;
    }

    function showEmailDialog() {

        if ( !alertify.emailDialog ) {


            var settingsFactory = function factory() {
                return{
                    build: function () {
                        // Move content from template
                        this.setContent( $( "#emailDialog" ).show()[0] ) ;
                        this.setting( {
                            'onok': function () {
                                //  alertify.notify( "Closing Windows" );
                            }
                        } ) ;
                    },
                    setup: function () {
                        return {
                            buttons: [ { text: "Close", className: alertify.defaults.theme.ok }
                            ],
                            options: {
                                title: "Email Addresses", resizable: false, movable: false, maximizable: false,
                            }
                        } ;
                    }

                } ;
            } ;


            alertify.dialog( 'emailDialog', settingsFactory, false, 'alert' ) ;
        }


        alertify.emailDialog().show() ;
        setTimeout( function () {
            $( "#emailText" ).css( "width", $( "#emailText" ).parent().outerWidth( true ) - 10 ) ;
            $( "#emailText" ).focus().select() ;
        }, 1000 ) ;

    }



    function showSettingsDialog() {
        if ( !alertify.reportSettings ) {


            var settingsFactory = function factory() {
                return{
                    build: function () {
                        // Move content from template
                        this.setContent( $( "#reportSettingsDialog" ).show()[0] ) ;
                        this.setting( {
                            'onok': function () {
                                //  alertify.notify( "Closing Windows" );
                            }
                        } ) ;
                    },
                    setup: function () {
                        return {
                            buttons: [ { text: "Close Settings", className: alertify.defaults.theme.ok }
                            ],
                            options: {
                                title: "Report Customization", resizable: false, movable: false, maximizable: false,
                            }
                        } ;
                    }

                } ;
            } ;


            alertify.dialog( 'reportSettings', settingsFactory, false, 'alert' ) ;
        }

        alertify.reportSettings().show() ;

    }

    function tableSorterMathFormat( $cell, wo, result, value, arry ) {
        //var txt = '<span class="align-decimal">' + result + '</span>';
        var txt = result ;
        if ( $cell.attr( 'data-prefix' ) != null ) {
            txt = $cell.attr( 'data-prefix' ) + txt ;
        }
        if ( $cell.attr( 'data-suffix' ) != null ) {
            txt += $cell.attr( 'data-suffix' ) ;
        }
        return txt ;
    }


// http://mottie.github.io/tablesorter/docs/
// selector plugin:
// http://mottie.github.io/tablesorter/docs/example-widget-column-selector.html
// 
    function initializeTablesWithSorting() {


        console.groupCollapsed( "Initializing tablesorter on reporting tables" ) ;
        $( "#useridTable" ).tablesorter( {
            sortList: [ [ 1, 1 ] ],
            theme: 'csapSummary',
            widgets: [ 'math' ],
            widgetOptions: {
                math_mask: '##0',
                math_data: 'math',
                columns_tfoot: false,
                math_complete: tableSorterMathFormat
            }
        } ) ;

        $( "#hostTable" ).tablesorter( {
            sortList: [ [ 7, 1 ] ],
            theme: 'csapSummary',
            widgets: [ 'math', 'columnSelector' ],
            widgetOptions: {
                columnSelector_container: $( '#hostColumnSelector' ),
                columnSelector_columns: {
                    0: 'disable',
                    1: 'disable',
                    2: false,
                    4: false,
                    9: false,
                    10: false,
                    14: false,
                    20: false,
                },
                columnSelector_mediaquery: false,
                columnSelector_saveColumns: false,
                math_mask: '#,###,##0.',
                math_data: 'math',
                columns_tfoot: false,
                math_complete: tableSorterMathFormat
            }
        } ) ;

        $( "#serviceSummaryTable" ).tablesorter( {
            sortList: [ [ 1, 0 ] ],
            theme: 'csapSummary',
            widgets: [ 'math', 'columnSelector' ],
            widgetOptions: {
                columnSelector_container: $( '#os-processColumnSelector' ),
                columnSelector_columns: {
                    0: 'disable',
                    1: 'disable'
                },
                columnSelector_mediaquery: false,
                columnSelector_saveColumns: false,
                math_mask: '#,###,##0.',
                math_data: 'math',
                columns_tfoot: false,
                math_complete: tableSorterMathFormat
            }
        } ) ;

        $( "#serviceDetailTable" ).tablesorter( {
            sortList: [ [ 1, 0 ] ],
            theme: 'csapSummary',
            widgets: [ 'math', 'columnSelector' ],
            widgetOptions: {
                columnSelector_container: $( '#os-processdetailColumnSelector' ),
                columnSelector_columns: {
                    0: 'disable',
                    1: 'disable',
                },
                columnSelector_saveColumns: false,
                columnSelector_mediaquery: false,
                math_mask: '#,###,##0.',
                math_data: 'math',
                columns_tfoot: false,
                math_complete: tableSorterMathFormat
            }
        } ) ;

        $( "#jmxSummaryTable" ).tablesorter( {
            sortList: [ [ 1, 0 ] ],
            theme: 'csapSummary',
            widgets: [ 'math', 'columnSelector' ],
            widgetOptions: {
                columnSelector_container: $( '#javaColumnSelector' ),
                columnSelector_columns: {
                    0: 'disable',
                    1: 'disable',
                    2: false,
                    3: false,
                    14: false,
                    15: false,
                    17: false,
                    18: false,
                    19: false
                },
                columnSelector_mediaquery: false,
                columnSelector_saveColumns: false,
                math_mask: '##0',
                math_mask: '#,###,##0.',
                columns_tfoot: false,
                math_complete: tableSorterMathFormat
            }
        } ) ;
        $( "#jmxDetailTable" ).tablesorter( {
            sortList: [ [ 1, 0 ] ],
            theme: 'csapSummary',
            widgets: [ 'math', 'columnSelector' ],
            widgetOptions: {
                columnSelector_container: $( '#javadetailColumnSelector' ),
                columnSelector_columns: {
                    0: 'disable',
                    1: 'disable',
                    2: false
                },
                columnSelector_saveColumns: false,
                columnSelector_mediaquery: false,
                math_mask: '#,###,##0.',
                math_data: 'math',
                columns_tfoot: false,
                math_complete: tableSorterMathFormat
            }
        } ) ;

        console.groupEnd( "Tables initialized" ) ;
    }



} ) ;
