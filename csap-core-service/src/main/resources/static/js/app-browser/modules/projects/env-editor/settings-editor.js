define( [ "browser/utils", "editor/validation-handler", "editor/json-forms" ], function ( utils, validationHandler, jsonForms ) {

    console.log( "Module loaded" ) ;
    let _editPanel = "#settingsEditor" ;
    let _container = "body" ;
    let _definitionTextAreaId = "#serviceJson" ;
    let _performanceLabels = null ;
    let HOST_REAL_TIME_LABEL = "HostRealTime" ;

    let _settingsJson = null ;

    let settings_loaded_defer = new $.Deferred() ;
    let _isJsonEditorActive = false ;

    let _currentDialog = null ;

    let _dialogId = "settingsDialog" ;


    let _defaultValues = {
        "configuration-maps": {
            "global": {
                "test-global-1": "test-global-value-1"
            },
            "map-for-testing": {
                "test-map-name-1": "test-map-value-1"
            }
        },

        "file-browser": {
            "csap home": {
                "cluster": "base-os",
                "folder": "/opt/csap",
                "group": "ldap-user-group"
            }
        },

        "metricsPublication": [
            {
                "type": "csap-health-report",
                "intervalInSeconds": 300,
                "url": "csap-health-report-uses-csap-events-url",
                "token": "csap-health-report-uses-csap-events-credentials"
            }
        ],
        "application": {
            "quick-launches": [
                {
                    "label": "Kubernetes Explorer",
                    "description": "Launch CSAP Kubernetes explorer",
                    "service": "kubelet",
                    "path": "/path-added-to-first-service-url"
                },
                {
                    "service": "csap-agent"
                },
                {
                    "service": "grafana"
                },
                {
                    "service": "alertmanager"
                },
                {
                    "service": "prometheus"
                },
                {
                    "label": "Log Analytics",
                    "service": "kibana"
                }
            ],

            "log-parsers": utils.getLogParsers(),

            "k8s-yaml-replacements": [
                {
                    "note:": "csap-monitoring requires this if quay.io is not accessible",
                    "original": "quay.io/",
                    "replacement": "your-server/"
                },
                {
                    "original": "image: quay.io/",
                    "replacement": "image: your-server/"
                }
            ]
        },

        "kubernetes-no": {
            "event-inspect-minutes": 5,
            "event-inspect-max-count": 1000,
            "event-inspect-excludes": [
                ".*cron"
            ]
        },

        "application-disks": [
            {
                "name": "csap home",
                "cluster": "base-os",
                "path": "/opt/csap"
            },
            {
                "name": "csap platform",
                "cluster": "base-os",
                "path": "/opt/csap/staging"
            },
            {
                "name": "shared disk",
                "cluster": "base-os",
                "path": "/mnt/CSAP_DEV01_NFS"
            }
        ],

        "vsphere": {

            "filters": {
                "vm-path": "vm/RNIs/CSAP-DEV_p",
                "datastore-regex": "(.*)CSAP(.*)"
            },

            "env": {
                "GOVC_USERNAME": "lab\\csapstorage",
                "GOVC_PASSWORD": "xxxxxxx",
                "GOVC_URL": "vcenter6.***REMOVED***",
                "GOVC_DATACENTER": "***REMOVED***",
                "GOVC_INSECURE": "1"
            }
        },

        "reports": {
            "realTimeMeters": [
                {
                    "label": "Host coresActive",
                    "id": "host.coresActive",
                    "intervals": [
                        3,
                        5,
                        10
                    ],
                    "min": 0
                },
                {
                    "label": "csap-agent Cpu (Total)",
                    "id": "os-process.topCpu_csap-agent",
                    "intervals": [
                        10,
                        30,
                        100
                    ]
                }
            ],
            "trending": [
                {
                    "label": "Cores Active",
                    "report": "custom/core",
                    "metric": "coresUsed",
                    "divideBy": "1"
                },
                {
                    "label": "Host Threads",
                    "report": "host",
                    "metric": "threadsTotal",
                    "divideBy": "numberOfSamples"
                },
                {
                    "label": "csap-agent Socket Count",
                    "report": "os-process/detail",
                    "metric": "socketCount",
                    "serviceName": "csap-agent",
                    "divideBy": "numberOfSamples"
                },
                {
                    "label": "csap-agent OS Commands",
                    "report": "application/detail",
                    "metric": "OsCommandsCounter",
                    "serviceName": "csap-agent",
                    "divideBy": "numberOfSamples"
                }
            ]
        }

    }

    return {
        //
        showSettingsDialog: function ( editDialogHtml ) {
            //when testing standalone, tests without
            if ( !jsonForms.isDialogBuilt( _dialogId ) ) {
                // do it only once.
                _container = ".ajs-dialog "
                _editPanel = _container + _editPanel ;
                _definitionTextAreaId = _container + _definitionTextAreaId ;
            }

            return showSettingsDialog( editDialogHtml ) ;
        },
        getSettingsDefinition: function () {
            getSettingsDefinition() ;
        },
        registerDialogButtons: function () {
            registerInputEvents() ;
        },
        updateSettingsDefinition: function () {
            updateSettingsDefinition( true ) ;
        },
        validateSettingsDefinition: function () {
            updateSettingsDefinition( false ) ;
        },
        configureForTest: function () {

            getSettingsDefinition() ;
            registerUiComponents() ;
            jsonForms.registerOperations( updateSettingsDefinition ) ;
        }
    }


    function refresh_layout() {
        jsonForms.resizeVisibleEditors( _definitionTextAreaId, _container, _editPanel ) ;
    }


    function registerUiComponents() {

        console.log( "registerUiComponents(): registering tab events" ) ;

        $( _editPanel ).tabs( {

            beforeActivate: function ( event, ui ) {

                if ( ui.oldTab.text().indexOf( "Editor" ) != -1 ) {
                    // refresh ui with edit changes.
                    console.log( "registerUiComponents():  parsing serviceJson" ) ;
                    _settingsJson = JSON.parse( $( _definitionTextAreaId ).val() ) ;
                    getSettingsDefinitionSuccess( _settingsJson ) ;
                }

            },

            activate: function ( event, ui ) {
                console.log( "registerUiComponents(): activating: " + ui.newTab.text() ) ;

                _isJsonEditorActive = false ;
                if ( ui.newTab.text().indexOf( "Editor" ) != -1 ) {
                    activateJsonEditor() ;
                }

                if ( ui.newTab.text().indexOf( "Landing Page" ) != -1 ) {
                    populateRealTimeTable() ;
                } else if ( ui.newTab.text().indexOf( "Trends" ) != -1 ) {
                    populateTrendingTable() ;
                } else {
                    refresh_layout() ;
                }

            }
        } ) ;

        $( ".dialogLifeSelect" ).selectmenu( {
            width: "200px",
            change: function () {
                console.log( "showSettingsDialog editing env: " + $( this ).val() ) ;
                getSettingsDefinition( $( this ).val() )
            }
        } ) ;


        registerInputEvents() ;

        $( _editPanel ).show() ;

        $.when( settings_loaded_defer ).done( function () {

            let rootPackageName = $( "a.project", "#project-selection" ).first().data( "name" ) ;
            console.log( `rootPackageName: ${rootPackageName}` ) ;
            if ( utils.getActiveProject() !== rootPackageName ) {
                console.log( `non root view - hiding root only panels` ) ;
                $( ".root-view", _editPanel ).hide() ;
                $( ".non-root-view", _editPanel ).show() ;

            } else {
                $( ".root-view", _editPanel ).show() ;
            }
            //activateTab( "landing" ) ;
            console.log( "settings_loaded_defer: updating post run actions" ) ;
            foldConfigMaps( 500 )

        } ) ;

    }

    function foldConfigMaps( delay = 100, doneOnce = false ) {
        setTimeout( function () {

            let $foldInput = $( "input.editor-toggle-last", _container ) ;
            console.log( `foldConfigMaps() - ${$foldInput.is( ':checked' )}` ) ;

            if ( !doneOnce && $foldInput.is( ':checked' ) ) {
                setTimeout( function () {
                    foldConfigMaps( 50, true )
                }, 10 ) ;
            }
            $foldInput.click() ;

        }, delay ) ;
    }

    function activateJsonEditor() {

        _isJsonEditorActive = true ;
        $( _definitionTextAreaId ).val( JSON.stringify( _settingsJson, null, "\t" ) ) ;
    }


    function showSettingsDialog( editDialogHtml ) {
        jsonForms.showDialog(
                _dialogId,
                refresh_layout,
                editDialogHtml,
                updateSettingsDefinition ) ;

        registerUiComponents() ;
        getSettingsDefinition( $( "#dialogLifeSelect" ).val() ) ;

    }

    function getSettingsConfiguration( lifeToEdit ) {

        console.log( `getSettingsConfiguration():  url '${settingsDefUrl}',  lifeToEdit: '${lifeToEdit}'` ) ;


        $.getJSON( settingsDefUrl + "/config", {
            lifeToEdit: lifeToEdit

        } ).done( function ( settingsConfig ) {
            let $servicesSelect = $( "#settingsTemplates select.services" ) ;
            $servicesSelect.empty() ;

            jQuery( '<option/>', {
                text: "All Services",
                value: "all"
            } ).appendTo( $servicesSelect ) ;

            console.log( `getSettingsConfiguration() Updating: services `, settingsConfig.services ) ;
            for ( let serviceName of settingsConfig.services ) {
                jQuery( '<option/>', {
                    text: serviceName
                } ).appendTo( $servicesSelect ) ;
            }

            $servicesSelect.sortSelect() ;

            _performanceLabels = settingsConfig.performanceLabels ;

            settings_loaded_defer.resolve() ;
            foldConfigMaps() ;

        } ).fail( function ( jqXHR, textStatus, errorThrown ) {

            handleConnectionError( "getSettingsDefinition ", errorThrown ) ;

        } ) ;
    }

    function getSettingsDefinition( lifeToEdit ) {

        console.log( "getSettingsDefinition(): " + settingsDefUrl + " lifeToEdit: " + lifeToEdit ) ;

        utils.loading( "Loading settings" ) ;

        $.getJSON( settingsDefUrl, {
            lifeToEdit: lifeToEdit,
            project: utils.getActiveProject()

        } ).done( function ( settingsJson ) {
            utils.loadingComplete() ;
            getSettingsDefinitionSuccess( settingsJson ) ;

            getSettingsConfiguration( lifeToEdit ) ;


        } ).fail( function ( jqXHR, textStatus, errorThrown ) {

            handleConnectionError( "getSettingsDefinition ", errorThrown ) ;
        } ) ;
    }

    function getSettingsDefinitionSuccess( settingsJson ) {
        _settingsJson = settingsJson ;

        if ( _settingsJson.lastModifiedBy == undefined ) {
            $( ".lastModified" ).hide() ;
        } else {
            $( ".lastModified .noteAlt" ).text( _settingsJson.lastModifiedBy ) ;
        }

        $( _definitionTextAreaId ).val( JSON.stringify( settingsJson, null, "\t" ) ) ;

        // update form values

        jsonForms.loadValues( _editPanel, settingsJson, _defaultValues ) ;


        //jsonForms.resizeVisibleEditors();
        refresh_layout() ;


        if ( $( "#jsonEditor" ).is( ":visible" ) ) {
            activateJsonEditor() ;
        }
        if ( $( "#realtime" ).is( ":visible" ) ) {
            populateRealTimeTable() ;
        }
        if ( $( "#trending" ).is( ":visible" ) ) {
            populateTrendingTable() ;
        }

    }

    function activateTab( tabId ) {
        let tabIndex = $( 'li[data-tab="' + tabId + '"]' ).index() ;

        console.log( "Activating tab: " + tabIndex ) ;

        // $("#jmx" ).prop("checked", true) ;

        $( _editPanel ).tabs( "option", "active", tabIndex ) ;

        return ;
    }

    function getDefinition() {
        console.log( "getDefinition()" ) ;
        return _settingsJson ;
    }
// Need to register events after dialog is loaded
    function registerInputEvents() {


        jsonForms.configureJsonEditors( getDefinition, _container, _editPanel, _definitionTextAreaId ) ;

        $( ".toggleRealTimeButton" ).click( function () {
            console.log( "toggleRealTimeButton toggling display" ) ;
            $( ".realTimeViewContainer" ).toggle() ;

            if ( $( "table.realTimeTable" ).is( ":visible" ) ) {
                //parseAndUpdateJsonEdits( $( ".realTimeViewContainer textarea" ) );
                jsonForms.parseAndUpdateJsonEdits( _settingsJson, $( ".realTimeViewContainer textarea" ) )
                $( ".toggleRealTimeButton" ).text( "Show Editor" ) ;
                populateRealTimeTable() ;
            } else {
                // update text contents
                getSettingsDefinitionSuccess( _settingsJson ) ;
                // resizeVisibleEditors();

                refresh_layout() ;
                $( ".toggleRealTimeButton" ).text( "Show Summary" ) ;
            }
        } ) ;
        $( ".toggleTrendingButton" ).click( function () {
            console.log( "toggleTrendingButton toggling display" ) ;
            $( ".trendingViewContainer" ).toggle() ;
            if ( $( "table.trendingTable" ).is( ":visible" ) ) {
                //parseAndUpdateJsonEdits( $( ".realTimeViewContainer textarea" ) );
                jsonForms.parseAndUpdateJsonEdits( _settingsJson, $( ".trendingViewContainer textarea" ) )
                $( ".toggleTrendingButton" ).text( "Show Editor" ) ;
                populateTrendingTable() ;
            } else {
                // update text contents
                getSettingsDefinitionSuccess( _settingsJson ) ;
                // resizeVisibleEditors();

                refresh_layout() ;
                $( ".toggleTrendingButton" ).text( "Show Summary" ) ;
            }
        } ) ;

        console.log( "registerInputEvents(): register events" ) ;

        $( _editPanel + " input," + _editPanel + " select" ).change( function () {
            jsonForms.updateDefinition( _settingsJson, $( this ) ) ;
        } ) ;

    }

    function buildEmptyMeterRow( $tbody ) {
        let $emptyColumn = jQuery( '<td/>', {
            colspan: 99,
            text: "Note: these will replace any imported environment meters" }
        ) ;

        let $newItemButton = jQuery( '<button/>', {
            class: "csap-button-icon csap-copy",
            text: "add meter" }
        ).css("margin-right", "5em") ;

        $newItemButton.prependTo( $emptyColumn ) ;

        $newItemButton.click( function () {
            utils.loading( "adding meter" ) ;
            let meters = [
                {
                    "label": "change-me",
                    "id": "host.coresActive",
                    "intervals": [
                        3,
                        5,
                        10
                    ],
                    "multiplyBy": 2,
                    "reverseColors": true
                }
            ]
            //reports.realTimeMeters
            $( "#setting-real-time-text" ).val( JSON.stringify( meters ) ) ;
            $( "#setting-real-time-text" ).trigger( "change" ) ;
            setTimeout( function () {
                populateRealTimeTable() ;
                utils.loadingComplete() ;
            }, 500 ) ;

        } ) ;

        return $emptyColumn   ;
    }


    function populateRealTimeTable() {
        console.log( "populateRealTimeTable Refresh" ) ;
        let $tbody = $( "table.realTimeTable tbody" ) ;
        $tbody.empty() ;

        let meters ;
        if ( _settingsJson.reports
                && Array.isArray( _settingsJson.reports.realTimeMeters )
                && _settingsJson.reports.realTimeMeters.length > 0 ) {
            meters = _settingsJson.reports.realTimeMeters ;
        } else {
            $tbody.append( buildEmptyMeterRow(  ) ) ;
            return ;

        }

        console.log( `meters: `, meters ) ;
        for ( let i = 0 ; i < meters.length ; i++ ) {

            let meter = meters[ i ] ;
            if ( !meter.id ) {
                alertify.alert( "Missing id field in item: " + i + " Use JSON Editor to correct." ) ;
                continue ;
            }

            let $meterRow = jQuery( '<tr/>', { 'data-order': i } ) ;
            $meterRow.appendTo( $tbody ) ;




            let $labelCell = jQuery( '<td/>', { } ) ;
            $labelCell.appendTo( $meterRow ) ;
            $labelCell.append( jQuery( '<button/>', {
                title: "Click/Drag to re-order", class: "csap-icon csap-indent moveRow"
            } ) ) ;

            $labelCell.append( jQuery( '<button/>', {
                title: "Add a new row", class: "csap-icon csap-copy newRow"
            } ) ) ;
            $labelCell.append( jQuery( '<button/>', {
                title: "Delete row", class: "csap-icon csap-trash deleteRow"
            } ) ) ;

            jQuery( '<div/>', {
                class: "tedit",
                "data-path": "reports.realTimeMeters[" + i + "].label",
                "data-iwidth": 150,
                text: meter.label
            } ).appendTo( $labelCell ) ;


            // real time has complicated ID
            let ids = meter.id.split( "." ) ;

            let performanceCategory = ids[0] ;
            $( "#settingsTemplates select.realtimeType" ).val( performanceCategory ) ;
//			console.log( "found realtime: ", performanceCategory, " select:",
//					$( "#settingsTemplates select.realtimeType" ).val() );

            if ( $( "#settingsTemplates select.realtimeType" ).val() != "" ) {
                let optionText = $( "#settingsTemplates select.realtimeType option:selected" ).text() ;
                if ( optionText.length > 0 ) {
//                    performanceCategory = optionText ;
                }
            }
            let attributes = ids[1].split( "_" ) ;
            let serviceName = "---" ;
            let collectItem = "update" ;
            console.log( `performanceCategory: '${performanceCategory}'` ) ;

            let labelCategory = performanceCategory ;
            switch ( performanceCategory ) {
                case "java" :
                    serviceName = attributes[1] ;
                    collectItem = attributes[0] ;
                    labelCategory = "Java" ;
                    break ;

                case "application" :
                    serviceName = ids[1] ;
                    collectItem = ids[2] ;
                    labelCategory = "Application" ;
                    break ;

                case "host" :
                    collectItem = ids[1] ;
                    labelCategory = "Host" ;
                    break ;

                case "os-process" :
                    serviceName = attributes[1] ;
                    collectItem = attributes[0] ;
                    labelCategory = "OS Process" ;
                    break ;

                default:
                    alertify.alert( "Invalid id field in item: " + i + " Use JSON Editor to correct: " + performanceCategory ) ;
                    //continue ;

            }

            if ( collectItem == undefined ) {
                collectItem = "update" ;
            }
            addEditableCell( $meterRow, "Service name for meter", serviceName, {
                path: "reports.realTimeMeters[" + i + "].id",
                iwidth: 100,
                servicename: serviceName,
                editvalue: serviceName,
                realtimefield: "service",
                realtimeid: meter.id,
                selectclass: "services",
            } ) ;

            let attributeLabel = findAttributeLabel( collectItem, labelCategory, collectItem, serviceName ) ;
            addEditableCell( $meterRow, "Performance Attribute", attributeLabel, {
                path: "reports.realTimeMeters[" + i + "].id",
                iwidth: 200,
                perfcategory: labelCategory,
                perfservice: serviceName,
                editvalue: collectItem,
                realtimefield: "attribute",
                realtimeid: meter.id,
            } ) ;

            addEditableCell( $meterRow, "Performance Category", labelCategory, {
                path: "reports.realTimeMeters[" + i + "].id",
                iwidth: 100,
                servicename: serviceName,
                editvalue: ids[0],
                realtimefield: "type",
                realtimeid: meter.id,
                selectclass: "realtimeType",
            } ) ;


            let $healthMeterCell = jQuery( '<td/>', { } ) ;
            $healthMeterCell.appendTo( $meterRow ) ;

            let healthMeter = "---" ;
            if ( meter.healthMeter )
                healthMeter = JSON.stringify( meter.healthMeter ) ;

            jQuery( '<div/>', {
                class: "tedit",
                "data-json": true,
                "data-path": "reports.realTimeMeters[" + i + "].healthMeter",
                text: healthMeter
            } ).appendTo( $healthMeterCell ) ;



            let $intervalsCell = jQuery( '<td/>', { } ) ;
            $intervalsCell.appendTo( $meterRow ) ;

            let intervals = "---" ;
            if ( meter.intervals )
                intervals = JSON.stringify( meter.intervals ) ;

            jQuery( '<div/>', {
                class: "tedit",
                "data-json": true,
                "data-path": "reports.realTimeMeters[" + i + "].intervals",
                text: intervals
            } ).appendTo( $intervalsCell ) ;


            let $reverseCell = jQuery( '<td/>', { } ) ;
            $reverseCell.appendTo( $meterRow ) ;

            let isReverse = "---" ;
            if ( meter.reverseColors ) {
                isReverse = meter.reverseColors ;
            }
            jQuery( '<div/>', {
                class: "tedit",
                "data-path": "reports.realTimeMeters[" + i + "].reverseColors",
                html: isReverse
            } ).appendTo( $reverseCell ) ;



            let $divideCell = jQuery( '<td/>', { } ) ;
            $divideCell.appendTo( $meterRow ) ;

            let divideBy = "---" ;
            if ( meter.divideBy ) {
                divideBy = meter.divideBy ;
            }
            jQuery( '<div/>', {
                class: "tedit",
                "data-path": "reports.realTimeMeters[" + i + "].divideBy",
                html: divideBy
            } ).appendTo( $divideCell ) ;




            let $multiplyCell = jQuery( '<td/>', { } ) ;
            $multiplyCell.appendTo( $meterRow ) ;

            let multiplyBy = "---" ;
            if ( meter.multiplyBy )
                multiplyBy = meter.multiplyBy ;
            jQuery( '<div/>', {
                class: "tedit",
                "data-path": "reports.realTimeMeters[" + i + "].multiplyBy",
                html: multiplyBy
            } ).appendTo( $multiplyCell ) ;


            let $scalingDisableCell = jQuery( '<td/>', { } ) ;
            $scalingDisableCell.appendTo( $meterRow ) ;

            let scalingDisplay = "---" ;
            if ( meter.disableScaling ) {
                scalingDisplay = meter.disableScaling ;
            }
            jQuery( '<div/>', {
                class: "tedit",
                "data-path": "reports.realTimeMeters[" + i + "].disableScaling",
                html: scalingDisplay
            } ).appendTo( $scalingDisableCell ) ;


        }

        registerTableEditEvents( $tbody, _settingsJson.reports, "realTimeMeters", populateRealTimeTable ) ;
    }

    function addEditableCell( $meterRow, title, label, attributes ) {

        let $cell = jQuery( '<td/>', { } ) ;
        $cell.appendTo( $meterRow ) ;

        let $editableDiv = jQuery( '<div/>', {
            class: "tedit",
            title: title,
            html: label
        } ) ;

        /**
         * path: used to update definition model
         * iwidth = input width, editvalue = value of input select
         * realtimeid = used for ID generation
         * selectclass: use template for generating select
         * perfcategory: generate select from performance labels
         * servicename: only needed if perfcategory == App
         * 
         */
        for ( let attribute in attributes ) {
            // $editableDiv.data( attribute, attributes[attribute] );
            $editableDiv.attr( "data-" + attribute, attributes[attribute] ) ;
        }

        $editableDiv.appendTo( $cell ) ;

    }

    function buildEmptyTrendRow( $tbody ) {
        let $emptyColumn = jQuery( '<td/>', {
            colspan: 99,
            text: "Note: these will replace any imported environment trends" }
        ) ;

        let $newItemButton = jQuery( '<button/>', {
            class: "csap-button-icon csap-copy",
            text: "add trend" }
        ).css("margin-right", "5em") ;

        $newItemButton.prependTo( $emptyColumn ) ;

        $newItemButton.click( function () {
            utils.loading( "adding trend" ) ;
            let trends = [
                {
                    "label": "Cores Used (all hosts)",
                    "report": "custom/core",
                    "metric": "coresUsed",
                    "about": "New item"
                }
            ]
            //reports.realTimeMeters
            $( "#settings-trends-text" ).val( JSON.stringify( trends ) ) ;
            $( "#settings-trends-text" ).trigger( "change" ) ;
            setTimeout( function () {
                populateTrendingTable() ;
                utils.loadingComplete() ;
            }, 500 ) ;

        } ) ;

        return $emptyColumn  ;
    }
    function populateTrendingTable() {
        console.log( "populateTrendingTable Refresh" ) ;
        let $tbody = $( "table.trendingTable tbody" ) ;
        $tbody.empty() ;

        let meters ;
        if ( _settingsJson.reports
                && Array.isArray( _settingsJson.reports.trending )
                && _settingsJson.reports.trending.length > 0 ) {
            meters = _settingsJson.reports.trending ;
        } else {
            $tbody.append( buildEmptyTrendRow() ) ;
            return ;
        }

        for ( let i = 0 ; i < meters.length ; i++ ) {

            let meter = meters[ i ] ;

            let $meterRow = jQuery( '<tr/>', { 'data-order': i } ) ;
            $meterRow.appendTo( $tbody ) ;




            let $labelCell = jQuery( '<td/>', { } ) ;
            $labelCell.appendTo( $meterRow ) ;
            $labelCell.append( jQuery( '<button/>', {
                title: "Click/Drag to re-order", class: "csap-icon csap-indent moveRow"
            } ) ) ;

            $labelCell.append( jQuery( '<button/>', {
                title: "Add a new row", class: "csap-icon csap-copy newRow"
            } ) ) ;
            $labelCell.append( jQuery( '<button/>', {
                title: "Delete row", class: "csap-icon csap-trash deleteRow"
            } ) ) ;

            jQuery( '<div/>', {
                class: "tedit",
                title: "Keep to a reasonable width for UI",
                "data-path": "reports.trending[" + i + "].label",
                "data-iwidth": 250,
                text: meter.label
            } ).appendTo( $labelCell ) ;



            let $nameCell = jQuery( '<td/>', { } ) ;
            $nameCell.appendTo( $meterRow ) ;

            let serviceName = "---" ;
            if ( meter.serviceName )
                serviceName = meter.serviceName ;
            jQuery( '<div/>', {
                class: "tedit",
                title: "name of service(s), comma separated",
                "data-selectclass": "services",
                "data-iwidth": 150,
                "data-path": "reports.trending[" + i + "].serviceName",
                html: serviceName
            } ).appendTo( $nameCell ) ;


            let $attributeCell = jQuery( '<td/>', { } ) ;
            $attributeCell.appendTo( $meterRow ) ;

            let attributeName = "---" ;
            if ( meter.metric ) {
                attributeName = meter.metric ;
            }

            let reportName = meter.report, report = meter.report ;

            $( "#settingsTemplates .trendType" ).val( report ) ;
            //console.log("trendType",  $("#settingsTemplates select.trendType").val()) ;
            if ( $( "#settingsTemplates select.trendType" ).val() != "" ) {
                let optionText = $( "#settingsTemplates select.trendType option:selected" ).text() ;
                if ( optionText.length > 0 ) {
                    reportName = optionText ;
                }
            }

            // attribute Names
            attributeName = findAttributeLabel( attributeName, reportName, meter.metric, serviceName ) ;

            jQuery( '<div/>', {
                class: "tedit",
                title: "name of attribute to trend",
                "data-perfservice": serviceName,
                "data-perfcategory": reportName,
                "data-editvalue": meter.metric,
                "data-iwidth": 150,
                "data-path": "reports.trending[" + i + "].metric",
                html: attributeName
            } ).appendTo( $attributeCell ) ;


            let $reportCell = jQuery( '<td/>', { } ) ;
            $reportCell.appendTo( $meterRow ) ;

            jQuery( '<div/>', {
                class: "tedit",
                title: "report id",
                "data-selectclass": "trendType",
                "data-editvalue": report,
                "data-path": "reports.trending[" + i + "].report",
                html: reportName
            } ).appendTo( $reportCell ) ;


//
//
            let $totalCell = jQuery( '<td/>', { } ) ;
            $totalCell.appendTo( $meterRow ) ;

            let isTotal = "---" ;
            if ( meter.allVmTotal )
                isTotal = meter.allVmTotal ;
            jQuery( '<div/>', {
                class: "tedit",
                title: "if true,  trend value will be the sum of all instances",
                "data-path": "reports.trending[" + i + "].allVmTotal",
                html: isTotal
            } ).appendTo( $totalCell ) ;
//
//
//
            let $divideCell = jQuery( '<td/>', { } ) ;
            $divideCell.appendTo( $meterRow ) ;

            let divideBy = "---" ;
            if ( meter.divideBy )
                divideBy = meter.divideBy ;
            jQuery( '<div/>', {
                class: "tedit",
                title: "numberOfSamples can be used to show the average collected value. Multiple values can be comma separated."
                        + "0.5 can be used to multiple",
                "data-path": "reports.trending[" + i + "].divideBy",
                html: divideBy
            } ).appendTo( $divideCell ) ;


        }

        registerTableEditEvents( $tbody, _settingsJson.reports, "trending", populateTrendingTable ) ;

    }

    function registerTableEditEvents( $tbody, definitionContainer, attribute, populateFunction ) {
        $tbody.sortable( {
            handle: "button.moveRow",
            cancel: '', //enables buttons for handles
            update: function ( event, ui ) {
                //console.log( "reorderd rows: ", ui );
                //saveLayout( $graphContainer, $plotContainer );
                let currentItems = definitionContainer[ attribute ] ;
                let updatedItemIndexOrder = new Array() ;
                $( "tr", $tbody ).each( function () {
                    updatedItemIndexOrder.push( $( this ).data( "order" ) ) ;
                } ) ;
                console.log( "row order: ", updatedItemIndexOrder ) ;
                let itemsInNewOrder = new Array() ;
                for ( let i = 0 ; i < updatedItemIndexOrder.length ; i++ ) {
                    itemsInNewOrder.push( currentItems[ updatedItemIndexOrder[i] ] ) ;
                }
                ;
                definitionContainer[ attribute ] = itemsInNewOrder ;

                console.log( "itemsInNewOrder: ", itemsInNewOrder ) ;
                // need to redraw - because moves use the table row number
                populateFunction() ;
            }
        } ) ;

        $( ".deleteRow", $tbody ).click( function () {
            let indexToDelete = $( this ).parent().parent().data( "order" ) ;

            let currentItems = definitionContainer[ attribute ] ;
            let itemsInNewOrder = new Array() ;
            for ( let i = 0 ; i < currentItems.length ; i++ ) {

                if ( i != indexToDelete )
                    itemsInNewOrder.push( currentItems[ i ] ) ;
            }
            ;
            definitionContainer[ attribute ] = itemsInNewOrder ;

            console.log( "itemsInNewOrder: ", itemsInNewOrder ) ;
            populateFunction() ;
        } ) ;


        $( ".newRow", $tbody ).click( function () {
            let indexToCopy = $( this ).parent().parent().data( "order" ) ;

            let currentItems = definitionContainer[ attribute ] ;
            let itemsInNewOrder = new Array() ;
            for ( let i = 0 ; i < currentItems.length ; i++ ) {

                itemsInNewOrder.push( currentItems[ i ] ) ;
                if ( i == indexToCopy ) {
                    // need a deep copy or same reference is in list twice.
                    let newObject = jQuery.extend( { }, currentItems[ i ] ) ;
                    itemsInNewOrder.push( newObject ) ;
                }
            }
            ;
            definitionContainer[ attribute ] = itemsInNewOrder ;

            console.log( "itemsInNewOrder: ", itemsInNewOrder ) ;
            populateFunction() ;

        } ) ;

        $( ".tedit", $tbody ).click( function () {
            makeItemEditable( $( this ), populateFunction ) ;
        } ) ;
    }

    function findAttributeLabel( labelDefault, reportName, metric, serviceName ) {
        let label = labelDefault ;

        console.log( `findAttributeLabel() labelDefault: ${labelDefault}, reportName: ${reportName}, metric: ${metric}, serviceName: ${serviceName}` )

        if ( reportName == "Application" ) {
            if ( _performanceLabels[reportName]
                    && _performanceLabels[reportName][serviceName ]
                    && _performanceLabels[reportName][serviceName ][metric] ) {
                label = _performanceLabels[reportName][serviceName ][metric] ;
            }
        } else if ( _performanceLabels[reportName] && _performanceLabels[reportName][metric] ) {
            label = _performanceLabels[reportName][metric] ;

        } else if ( reportName == "Host"
                && _performanceLabels[HOST_REAL_TIME_LABEL]
                && _performanceLabels[HOST_REAL_TIME_LABEL][metric] ) {
            // alternal labels for real time graphs
            label = _performanceLabels[HOST_REAL_TIME_LABEL][metric] ;
        }

        if ( metric == "diskTest" ) {
            console.log( "found label:", label, " list", _performanceLabels[HOST_REAL_TIME_LABEL], metric ) ;
        }
        console.log( `label: ${label}` ) ;

        return label
    }

    function makeItemEditable( $itemSelected, populateFunction ) {

        console.log( "makeItemEditable all data: ", $itemSelected.data() ) ;

        let content = $itemSelected.text() ;
        if ( content == "---" )
            content = "---" ;
        let editValue = $itemSelected.data( "editvalue" ) ;
        if ( editValue != undefined ) {
            content = editValue ;
        }
        let path = $itemSelected.data( "path" ) ;
        $itemSelected.off() ;
        $itemSelected.empty() ;


        let selectClass = $itemSelected.data( "selectclass" ) ;
        let perfCategory = $itemSelected.data( "perfcategory" ) ;
        let isRealTime = $itemSelected.data( "realtimefield" ) != undefined ;
        let $editValueContainer = null ;

        console.log( `perfCategory: ${perfCategory}, labels`, _performanceLabels )

        if ( perfCategory != undefined && _performanceLabels[perfCategory] ) {
            // generate select

            console.log( "isRealTime now:", isRealTime, "Generating select using performance", perfCategory
                    , " current value: ", editValue ) ;
            $editValueContainer = jQuery( '<select/>', {
                class: "",
                "data-json": $itemSelected.data( "json" ),
                "data-path": path
            } )

            let labelKey = perfCategory ;
            if ( isRealTime && perfCategory == "Host" ) {
                labelKey = HOST_REAL_TIME_LABEL ;
            }
            // let options = $( "#settingsTemplates select." + selectClass ).html();
            let attributes = _performanceLabels[labelKey] ;
            console.log( "attributes", attributes ) ;
            if ( perfCategory == "Application" ) {
                attributes = attributes [ $itemSelected.data( "perfservice" ) ] ;
            }
            for ( let attributeId in attributes ) {
                jQuery( '<option/>', {
                    value: attributeId,
                    text: attributes[attributeId]
                } ).appendTo( $editValueContainer ) ;
            }

            $editValueContainer.val( content ) ;
            $editValueContainer.change( function () {

                setTimeout( function () {
                    populateFunction() ;
                }, 1000 ) ;
            } ) ;
            $editValueContainer.sortSelect() ;

        } else if ( selectClass != undefined && selectClass != "none" ) {
            console.log( `Generating select using selectclass ${ selectClass }`, editValue ) ;

            $editValueContainer = jQuery( '<select/>', {
                class: "",
                "data-json": $itemSelected.data( "json" ),
                "data-path": path
            } )

            let options = $( "#settingsTemplates select." + selectClass ).html() ;
            $editValueContainer.html( options ) ;


            // console.log( options );

            $editValueContainer.val( content ) ;
            $editValueContainer.change( function () {

                setTimeout( function () {
                    populateFunction() ;
                }, 1000 ) ;
            } ) ;
        } else {
            // default to input
            $editValueContainer = jQuery( '<input/>', {
                class: "",
                "data-json": $itemSelected.data( "json" ),
                "data-path": path,
                value: content
            } )
        }
        $editValueContainer.change( function () {
            let realTimeId = $itemSelected.data( "realtimeid" ) ;
            let oldValue = $itemSelected.data( "editvalue" ) ;
            let field = $itemSelected.data( "realtimefield" ) ;
            let newValue = $( this ).val() ;

            if ( realTimeId != undefined && oldValue != undefined ) {

                let newId = realTimeId.replace( oldValue, newValue ) ;
                if ( field == "type" ) {
                    let serviceName = $itemSelected.data( "servicename" ) ;
                    // need to reorder based on type
                    switch ( newValue ) {
                        case "os-process" :
                            newId = "os-process.UpdateThis_" + serviceName ;
                            break ;

                        case "application" :
                            newId = "application." + serviceName + ".UpdateThis" ;
                            break ;

                        case "java" :
                            newId = "java.UpdateThis_" + serviceName ;
                            break ;

                        case "host" :
                            newId = "host.UpdateThis" ;
                            break ;
                    }
                }
                let $realTimeInput = $editValueContainer = jQuery( '<input/>', {
                    "data-path": path,
                    value: newId
                } ) ;
                jsonForms.updateDefinition( _settingsJson, $realTimeInput ) ;

            } else {
                jsonForms.updateDefinition( _settingsJson, $( this ) ) ;
            }
        } ) ;

        let inputWidth = Math.round( $itemSelected.parent().outerWidth() - 30 ) ;
        if ( editValue != undefined ) {
            inputWidth = content.length * 7 ;
        }
        let iwidth = $itemSelected.data( "iwidth" ) ;
        if ( iwidth != undefined ) {
            inputWidth = iwidth ;
        }
//        $editValueContainer.css( "width", inputWidth ) ;
//        $editValueContainer.css( "margin-right", 0 ) ;
        $itemSelected.append( $editValueContainer ) ;

    }



    function updateSettingsDefinition( operation, isUpdate, globalDefinitionUpdateFunction, newName, message ) {

        if ( jsonForms.areThereErrors() ) {
            return ;
        }
        // need sync _serviceJson with editors
        if ( _isJsonEditorActive ) {
            // only submit if parsing is passing
            // definitionJson, $jsonTextArea, definitionDomId
            if ( !jsonForms.parseAndUpdateJsonEdits( _settingsJson, $( _definitionTextAreaId ), _definitionTextAreaId ) ) {
                alertify.alert( "Parsing errors must be corrected prior to further processing" ) ;
                return ;
            }
            console.log( "updateSettingsDefinition() - setting json" ) ;
            _settingsJson = JSON.parse( $( _definitionTextAreaId ).val() ) ;

        }

        let lifeToEdit = $( "#dialogLifeSelect" ).val() ;
        let paramObject = {
            lifeToEdit: lifeToEdit,
            project: utils.getActiveProject(),
            definition: JSON.stringify( _settingsJson, null, "\t" ),
            message: "Lifecycle Settings: " + message
        } ;

        console.log( "updateSettingsDefinition(): ", paramObject ) ;

        if ( operation == "notify" ) {
            $.extend( paramObject, {
                itemName: paramObject.lifeToEdit,
                hostName: "*"
            } )
            $.post( clusterDefUrl + "/../notify", paramObject )
                    .done( function ( updatesResult ) {
                        alertify.alert( "Changes Submitted For Review", JSON.stringify( updatesResult, null, "\t" ) ) ;
                    } )
                    .fail( function ( jqXHR, textStatus, errorThrown ) {
                        alertify.alert( "Failed Operation: " + jqXHR.statusText, "Contact your administrator" ) ;
                    } ) ;
            return false ;
        }

        let resultsTitle = "Results for Operation: " + operation + ", Lifecycle: " + lifeToEdit ;
        if ( isUpdate ) {
            $.extend( paramObject, {
                isUpdate: isUpdate
            } ) ;
        } else {
            resultsTitle += " - Validation Only" ;
        }

        utils.loading( `Performing: ${ operation }` ) ;

        $.post( settingsDefUrl, paramObject )

                .done( function ( updatesResult ) {

                    utils.loadingComplete() ;


                    let $userMessage = validationHandler.processValidationResults( updatesResult.validationResults ) ;

                    let $debugInfo = jQuery( '<div/>', {
                        class: "debugInfo",
                        text: "*details...." + JSON.stringify( updatesResult, null, "\t" )
                    } ) ;

                    let okFunction = function () {
                        console.log( "Closed results" ) ;
                        if ( isUpdate ) {
                            jsonForms.closeDialog() ;
                        }
                    }

                    $userMessage.append( $debugInfo ) ;
                    if ( updatesResult.updatedHost ) {
                        let $moreInfo = $( "#dialogResult" ).clone().css( "display", "block" ) ;
                        $( ".noteAlt", $moreInfo ).text( updatesResult.updatedHost ) ;
                        okFunction = function () {
                            console.log( "Closed results" ) ;
                            jsonForms.closeDialog() ;
                        }
                        $userMessage.append( $moreInfo ) ;
                    }

                    if ( globalDefinitionUpdateFunction != null ) {
                        globalDefinitionUpdateFunction( true ) ;
                    }

                    //alertify.alert( resultsTitle, $userMessage.html(), okFunction );
                    jsonForms.showUpateResponseDialog( resultsTitle, $userMessage.html(), okFunction ) ;


                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    handleConnectionError( "updating" + settingsDefUrl, errorThrown ) ;
                    alertify.error( "Failed" ) ;
                } ) ;
    }
    ;


} ) ;
