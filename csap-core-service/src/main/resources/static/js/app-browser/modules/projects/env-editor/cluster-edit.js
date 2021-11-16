define( [ "browser/utils", "editor/validation-handler", "editor/json-forms" ], function ( utils, validationHandler, jsonForms ) {

    console.log( "Module loaded" ) ;

    let _clusterEditorSelector = "#clusterEditor" ;

    let $clusterEditor, $dialogHeader ;
    let _dialogSelector = "body" ;

    let _definitionSelector = "#serviceJson" ;

    let _settingsJson = null ;
    let _isJsonEditorActive = false ;

    let _currentDialog = null ;

    const _dialogId = "clusterEditorDialog" ;

    let _refreshLifecycleFunction = null ;

    let _defaultCluster = {
        "type": "simple",
        "template-references": [ ],
        "hosts": [ ]
    }


    return {
        setRefresh: function ( refreshFunction ) {
            console.log( "_refreshLifecycleFunction updated" ) ;
            _refreshLifecycleFunction = refreshFunction ;
        },
        //
        show: function ( editDialogHtml ) {
            //when testing standalone, tests without
            if ( !jsonForms.isDialogBuilt( _dialogId ) ) {
                // do it only once.
                _dialogSelector = ".ajs-dialog "
                _clusterEditorSelector = _dialogSelector + _clusterEditorSelector ;
                _definitionSelector = _dialogSelector + _definitionSelector ;
            }

            return showDialog( editDialogHtml ) ;
        },
        getClusterDefinition: function () {
            getClusterDefinition() ;
        },
        registerDialogButtons: function () {
            registerInputEvents() ;
        },
        updateClusterDefinition: function () {
            updateClusterDefinition( true ) ;
        },
        validateClusterDefinition: function () {
            updateClusterDefinition( false ) ;
        },
        configureForTest: function () {

            getClusterDefinition(
                    utils.getActiveProject(),
                    $( ".lifeSelection" ).val(),
                    $( "#dialogClusterSelect" ).val() ) ;
            registerUiComponents() ;

            jsonForms.registerOperations( updateClusterDefinition ) ;
        }
    }


    function refresh_layout( $source ) {
        utils.loading( "updating..." ) ;

        if ( $source ) {
            console.log( `updated ${ $source.attr( "id" ) }, reloading form fields` ) ;
            jsonForms.loadValues( _clusterEditorSelector, _settingsJson ) ;

        }

        setTimeout( function () {
            console.log( `refresh_layout`, _settingsJson ) ;
            jsonForms.resizeVisibleEditors( _definitionSelector, _dialogSelector, _clusterEditorSelector ) ;
            utils.loadingComplete() ;
        }, 100 )
    }


    function registerUiComponents() {

        console.log( `registerUiComponents() building ${_clusterEditorSelector}` )

        $clusterEditor = $( _clusterEditorSelector ) ;

        $dialogHeader = $( "#dialogOpsContainer", $clusterEditor.parent() ) ;


        $clusterEditor.tabs( {

            beforeActivate: function ( event, ui ) {

                if ( ui.oldTab.text().indexOf( "Editor" ) != -1 ) {

                    console.log( "\n\n SPEC EDITOR: was last tab; reloading current definition" ) ;

                    // _settingsJson = JSON.parse( $( _definitionSelector ).val() ) ;
                    const currentDefinition = jsonForms.getJsonText( $( _definitionSelector ) ) ;
                    _serviceJson = JSON.parse( currentDefinition ) ;
                    getClusterDefnSuccess( _settingsJson ) ;
                }

            },

            activate: function ( event, ui ) {
                console.log( `\n\n activating tab: ${ ui.newTab.text() } ` ) ;

                _isJsonEditorActive = false ;


                if ( ui.newTab.text().indexOf( "Editor" ) != -1 ) {
                    activateJsonEditor() ;
                }

                refresh_layout() ;

            }
        } ) ;

        //$( "select.dialogClusterSelect" ).sortSelect() ;
        $( "select.dialogClusterSelect", $dialogHeader ).sortSelect() ;
        $( "select.dialogClusterSelect", $dialogHeader ).change( function () {
            console.log( "registerUiComponents editing env: " + $( this ).val() ) ;
            getClusterDefinition(
                    utils.getActiveProject(),
                    $( "#lifeEdit" ).val(),
                    $( this ).val() ) ;

        } ) ;


        let serviceTemplateSelectedFunction = function () {

            let serviceName = $( this ).val() ;
            console.log( "adding service: " + serviceName ) ;

            let $serviceText = $( "#osClusterText", $clusterEditor ) ;
            let servicePath = $serviceText.data( "path" ) ;

            if ( !_settingsJson[ servicePath ] ) {
                _settingsJson[ servicePath ] = new Array() ;
            }

            if ( $.inArray( serviceName, _settingsJson[ servicePath ] ) != -1 ) {
                alertify.alert( "Service is already in the cluster: " + serviceName ) ;
                return ;
            }

            _settingsJson[ servicePath ].push( serviceName ) ;
            if ( serviceName != "default" ) {
                $( this ).val( "default" ) ;
            }

            refresh_layout( $serviceText ) ;
        }

        $( ".osAddSelect", $clusterEditor ).sortSelect() ;
        $( ".osAddSelect", $clusterEditor ).change( serviceTemplateSelectedFunction ) ;

        $( "#show-cluster-attributes", $clusterEditor ).click( function () {
            console.log( `toggling display of base-cluster` ) ;
            $( ".base-cluster", $clusterEditor ).show() ;
            $( ".base-cluster-no-type", $clusterEditor ).hide() ;

        } )


        let $hostText = $( "#hostText", $clusterEditor ) ;
        $( ".addHostButton", $clusterEditor ).click( function () {

            let message = 'Enter the host name: ' ;

            if ( addHostUrl && addHostUrl.contains( "http" ) ) {
                message += '<a id="hostCatalog" href="' + addHostUrl
                        + '" target="_blank" class="simple" ><img src="' + baseUrl + '/images/16x16/document-new.png">Order</a>' ;
            }
            alertify.prompt( "New Host", message, "newHostName",
                    function ( evt, newHostName ) {

                        let hostPath = $hostText.data( "path" ) ;
                        console.log( 'newHostName: ' + newHostName + " updating path: " + hostPath ) ;

                        let currentHosts = jsonForms.getValue( hostPath, _settingsJson ) ;

                        if ( $.inArray( newHostName, currentHosts ) != -1 ) {
                            alertify.alert( "Host is already in the cluster: " + newHostName ) ;
                            return ;
                        }

                        currentHosts.push( newHostName ) ;

                        refresh_layout( $hostText ) ;

                    },
                    function () {
                        console.log( "canceled" ) ;
                    }
            ) ;
        } ) ;

        let newHostSelected = function () {
            let newHostName = $( this ).val() ;

            let hostPath = $hostText.data( "path" ) ;
            console.log( 'newHostName: ' + newHostName + " updating path: " + hostPath ) ;

            let currentHosts = jsonForms.getValue( hostPath, _settingsJson ) ;

            if ( $.inArray( newHostName, currentHosts ) != -1 ) {
                alertify.alert( "Host is already in the cluster: " + newHostName ) ;
                return ;
            }

            currentHosts.push( newHostName ) ;

            if ( newHostName != "default" ) {
                $( this ).val( "default" ) ;
            }

            refresh_layout( $hostText ) ;
        }
        $( ".hostAddSelect", $clusterEditor ).sortSelect() ;
        $( ".hostAddSelect", $clusterEditor ).change( newHostSelected ) ;



        registerInputEvents() ;

        $clusterEditor.show() ;

    }


    function activateJsonEditor() {

        console.log( `\n\n updating json text area` ) ;
        _isJsonEditorActive = true ;
        $( _definitionSelector ).val( JSON.stringify( _settingsJson, null, "\t" ) ) ;

    }

    function showDialog( editDialogHtml ) {

        console.log( `building dialog` ) ;

        if ( $( _definitionSelector ).length > 0 ) {
            alertify.csapWarning( `found existing textaread ${_definitionSelector}, reload browser` ) ;
        }

        jsonForms.showDialog(
                _dialogId,
                refresh_layout,
                editDialogHtml,
                updateClusterDefinition ) ;


        registerUiComponents() ;

        let isAddMode = $( ".addDefButton" ).is( ":visible" ) ;
        if ( !isAddMode ) {
            getClusterDefinition(
                    utils.getActiveProject(),
                    $( ".lifeSelection" ).val(),
                    $( "#dialogClusterSelect" ).val() ) ;
        } else {
            $( ".serviceLoading" ).hide() ;
            //getServiceDefinitionSuccess( _defaultService );
            getClusterDefnSuccess( copy_object( _defaultCluster ) ) ;
        }


    }

    function copy_object( the_object ) {
        return JSON.parse( JSON.stringify( the_object ) ) ;
    }


    function getClusterDefinition( releasePackage, lifeToEdit, clusterName ) {

        console.log( "getClusterDefinition() : " + clusterDefUrl + " lifeToEdit: " + lifeToEdit ) ;

        utils.loading( "Loading Cluster Definition" ) ;
//	if ( $( '#serviceJson' ).val() != "loading..." )
//		return;
        //$( ".loading" ).html( "Retrieving capability definition from server" ).show();
        $( ".clusterLoading" ).show() ;
        $.getJSON( clusterDefUrl, {
            project: releasePackage,
            lifeToEdit: lifeToEdit,
            clusterName: clusterName
        } )

                .done( function ( serviceJson ) {
                    utils.loadingComplete() ;
                    getClusterDefnSuccess( serviceJson ) ;
                } )

                .fail(
                        function ( jqXHR, textStatus, errorThrown ) {

                            handleConnectionError( "getClusterDefinition ", errorThrown ) ;
                        } ) ;
    }

    function getClusterDefnSuccess( clusterDefJson ) {
        _settingsJson = clusterDefJson ;

        $( _definitionSelector ).val( JSON.stringify( clusterDefJson, null, "\t" ) ) ;
        jsonForms.loadValues( _clusterEditorSelector, _settingsJson ) ;

        // new cluster format 
        let clusterType = clusterDefJson.type ;

        if ( !clusterType ) {

            $( ".base-cluster", $clusterEditor ).hide() ;
            $( ".base-cluster-no-type", $clusterEditor ).show() ;

        } else {

            $( ".base-cluster", $clusterEditor ).show() ;
            $( ".base-cluster-no-type", $clusterEditor ).hide() ;
            if ( clusterType == "kubernetes-provider" ) {
                $( ".cluster-k8-details", $clusterEditor ).hide() ;
                $( ".cluster-host-textarea", $clusterEditor ).parent().parent().show() ;
                $( ".cluster-k8-masters", $clusterEditor ).show() ;

            } else if ( clusterType == "kubernetes" ) {

                $( ".cluster-host-textarea", $clusterEditor ).parent().parent().hide() ;
                $( ".cluster-k8-details", $clusterEditor ).show() ;
                $( ".cluster-k8-masters", $clusterEditor ).hide() ;

            } else {
                $( ".cluster-host-textarea", $clusterEditor ).parent().show() ;
                $( ".cluster-k8-details", $clusterEditor ).hide() ;
                $( ".cluster-k8-masters", $clusterEditor ).hide() ;
            }
        }

        $( ".clusterTypeSelect", $clusterEditor ).val( clusterType ) ;
        $( ".clusterTypeSelect", $clusterEditor ).data( "last", clusterType ) ;

        if ( $( "#jsonEditor", $clusterEditor ).is( ":visible" ) ) {
            activateJsonEditor() ;
        }

        $( ".clusterLoading" ).hide() ;

        refresh_layout() ;

    }


    function getDefinition() {
        console.log( "getDefinition()" ) ;
        return _settingsJson ;
    }
// Need to register events after dialog is loaded
    function registerInputEvents() {


        jsonForms.configureJsonEditors( getDefinition, _dialogSelector, _clusterEditorSelector, _definitionSelector ) ;

        console.log( "registerInputEvents(): register events" ) ;

        $( "input, select", $clusterEditor ).change( function () {
            jsonForms.updateDefinition( _settingsJson, $( this ) ) ;
        } ) ;

        $( ".clusterTypeSelect", $clusterEditor ).change( function () {
            getClusterDefnSuccess( _settingsJson )
        } ) ;

    }



    function getValueOrDefault( value ) {
        if ( value == undefined || value == "" ) {
            return "---"
        }
        return value ;
    }


    function updateClusterDefinition( operation, isUpdate, globalDefinitionUpdateFunction, newName, message ) {


        if ( jsonForms.areThereErrors() ) {
            return ;
        }
        // need sync _serviceJson with editors
        if ( _isJsonEditorActive ) {
            // only submit if parsing is passing
            // definitionJson, $jsonTextArea, definitionDomId
            if ( !jsonForms.parseAndUpdateJsonEdits( _settingsJson, $( _definitionSelector ), _definitionSelector ) ) {
                alertify.alert( "Parsing errors must be corrected prior to further processing" ) ;
                return ;
            }
            console.log( "updateClusterDefinition() - setting json" ) ;
            _settingsJson = JSON.parse( $( _definitionSelector ).val() ) ;

        }


        let paramObject = {
            operation: operation,
            newName: newName,
            project: utils.getActiveProject(),
            lifeToEdit: $( ".lifeSelection" ).val(),
            clusterName: $( "#dialogClusterSelect" ).val(),
            definition: JSON.stringify( _settingsJson, null, "\t" ),
            message: "Cluster Settings: " + message
        } ;

        console.log( "updateClusterDefinition(): ", paramObject ) ;

        if ( operation == "notify" ) {
            $.extend( paramObject, {
                itemName: paramObject.clusterName,
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

        let resultsTitle = "Results for Operation: " + operation ;
        if ( isUpdate ) {
            $.extend( paramObject, {
                isUpdate: isUpdate
            } ) ;
        } else {
            resultsTitle += "- Validation Only" ;
        }

        utils.loading( `Performing: ${ operation }` ) ;
        $.post( clusterDefUrl, paramObject )
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
//                        let $moreInfo = $( "#dialogResult" ).clone().css( "display", "block" ) ;
//                        $( ".noteAlt", $moreInfo ).text( updatesResult.updatedHost ) ;
                        let $moreInfo = jQuery( '<div/>', {
                            class: "csap-white",
                            text: "Update host: " + updatesResult.updatedHost
                        } ) ;
                        if ( updatesResult.notes ) {
                            jQuery( '<div/>', {
                                class: "code",
                                text: "Notes: " + JSON.stringify( updatesResult.notes, "\n", "  " )
                            } ).appendTo( $moreInfo ) ;
                        }
                        $userMessage.append( $moreInfo ) ;



                        if ( operation != "modify" ) {
                            // $userMessage.append( "<br/><br/>Remember to kill/clean services before applying changes to cluster" );
                            okFunction = function () {
                                if ( _refreshLifecycleFunction != null ) {
                                    console.log( "dialog OK pressed: triggering refresh function" ) ;
                                    _refreshLifecycleFunction() ;

                                } else {
                                    console.log( "dialog OK pressed:  - warning:  refresh function is null" ) ;
                                }
                                jsonForms.closeDialog() ;
                            }
                        } else {
                            okFunction = function () {
                                console.log( "Closed results" ) ;
                                if ( _refreshLifecycleFunction != null ) {
                                    _refreshLifecycleFunction() ;
                                }
                            }
                        }
                    }

                    if ( globalDefinitionUpdateFunction != null ) {
                        globalDefinitionUpdateFunction( true ) ;
                    }

                    // alertify.alert( resultsTitle, $userMessage.html(), okFunction );
                    jsonForms.showUpateResponseDialog( resultsTitle, $userMessage.html(), okFunction ) ;



                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    handleConnectionError( "updating" + clusterDefUrl, errorThrown ) ;
                    alertify.error( "Failed" ) ;
                } ) ;
    }
    ;


} ) ;



