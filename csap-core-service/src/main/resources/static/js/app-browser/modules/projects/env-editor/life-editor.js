
let requireJsDefinitionFiles = [ "browser/utils", "projects/env-editor/service-edit", "projects/env-editor/settings-editor", "projects/env-editor/cluster-edit", "editor/json-forms", "editor/validation-handler" ] ;
define( requireJsDefinitionFiles, function ( utils, serviceEdit, settingsEdit, clusterEditor, jsonForms, validationHandler ) {

    console.log( "Module loaded" ) ;

    const $targetContainer = $( "#lifeEditor" ) ;
    let $clusterFilter, _clusterFilterTimer ;


    let _lastEnvironmentSelected = null, _lastRelase = null, _lastFilter ;

    let initialized = false ;

    let _refreshFunction = function () {
        console.log( "_refreshFunction: Reloading summary view" ) ;
        setTimeout( function () {
            loadEnvironmentTemplate( _lastEnvironmentSelected, _lastRelase ) ;
        }, 500 )
    } ;

    let _lastSummary = null ;

    return {
        //
        showSummaryView: function ( environment, container, project ) {

            //$targetContainer = container ;
            utils.setServiceEditorFunction( launchServiceEditor ) ;
            if ( !initialized ) {
                initialize() ;
            }
            loadEnvironmentTemplate( environment, project ) ;

        },
    }

    function initialize() {

        $( "#include-csap-templates-checkbox" ).change( function () {
            initialized = true ;
            loadEnvironmentTemplate( _lastEnvironmentSelected, _lastRelase ) ;
        } ) ;
    }

    function loadEnvironmentTemplate( environment, releasePackage ) {
        // alertify.notify( "Loading: " + life );

        utils.loading( "loading environment" ) ;

        if ( !environment ) {

            if ( _lastEnvironmentSelected ) {
                environment = _lastEnvironmentSelected ;
            } else {
                environment = utils.getActiveEnvironment() ;
            }
        }

        _lastEnvironmentSelected = environment, _lastRelase = releasePackage ;

        if ( $clusterFilter ) {
            _lastFilter = $clusterFilter.val() ;
        }

        let includeTemplates = $( "#include-csap-templates-checkbox" ).is( ":checked" ) ;

        let params = {
            lifeToEdit: environment,
            project: releasePackage,
            includeTemplates: includeTemplates
        } ;

        console.log( `loading environment: '${ environment }', url: ${lifeEditUrl}` ) ;
        $.get( lifeEditUrl,
                params,
                function ( lifeDialogHtml ) {
                    showSummaryView( lifeDialogHtml ) ;
                    $( "#include-csap-templates-checkbox" ).prop( "checked", includeTemplates ) ;
                },
                'html' ) ;
    }

    function showSummaryView( lifeEditView ) {

        _lastSummary = lifeEditView ;



        let $batchContent = $( "<html/>" ).html( lifeEditView ).find( '#lifeEditorWrapper' ) ;
        console.log( "showSummaryView(): content Length: " + $batchContent.text().length ) ;

        $targetContainer.html( $batchContent ) ;

        // note event registration is done each time as the contents are replaced
        let $lifeTable = $( "#editLifeTable", $targetContainer ) ;
        $clusterFilter = $( "#clusterFilter", $lifeTable ) ;

        console.log( "clusterHostName width: ", $( ".clusterHostName" ).first().outerWidth( true ) ) ;


        console.log( `initialize() Registering Events ${ $clusterFilter.length }` ) ;
        $clusterFilter.off().keyup( applyClusterFilter ) ;

        if ( _lastFilter ) {
            $clusterFilter.val( _lastFilter ) ;
            $clusterFilter.trigger( "keyup" ) ;
        }

        utils.loadingComplete() ;

        $( ".lifeSelection" ).selectmenu( {
            change: function () {
                console.log( "showLifeEditor editing env: " + $( this ).val() ) ;
                loadEnvironmentTemplate( $( this ).val(), utils.getActiveProject() ) ;
            }
        } ) ;

        registerLifeOperations() ;

        // console.log("showSummaryView: hostName: " + hostName + " serviceName: " + serviceName);
        serviceEdit.setRefresh( _refreshFunction ) ;
        $( '.editServiceButton' ).click( function () {
            let serviceName = $( this ).text().trim() ;
            launchServiceEditor( serviceName ) ;
            return false ;
        } ) ;



        $( ".addServiceClusterButton", $targetContainer ).click( function () {
            console.log( "Adding servicex" ) ;
            alertify.prompt( "Add a new service", 'Enter the name', "my-new-service",
                    function ( evt, newServiceName ) {
                        console.log( 'newServiceName: ' + newServiceName ) ;
                        let params = {
                            project: utils.getActiveProject(),
                            serviceName: newServiceName,
                            "newService": "newService",
                            hostName: "*"
                        }
                        $.get( serviceEditUrl,
                                params,
                                function ( dialogHtml ) {
                                    serviceEdit.showServiceDialog( newServiceName, dialogHtml )
                                },
                                'html' ) ;
                    },
                    function () {
                        console.log( "canceled" ) ;
                    }
            ) ;
            return false ;
        } ) ;


        $( '#editSettingsButton' ).click( function () {

            let lifeEdit = $( "#lifeEdit" ).val() ;

            console.log( "\n\nshowSummaryView(): launching: " + settingsEditUrl + " lifeEdit: " + lifeEdit ) ;

            let params = {
                lifeToEdit: lifeEdit,
                project: utils.getActiveProject()
            }

            utils.loading( `Loading Settings...` ) ;

            $.get( settingsEditUrl,
                    params,
                    function ( htmlPage ) {
                        utils.loadingComplete() ;
                        settingsEdit.showSettingsDialog( htmlPage ) ;
                    },
                    'html' ) ;

            //}
        } ) ;


        $( '.editClusterButton' ).click( function () {

            let lifeEdit = $( "#lifeEdit" ).val() ;
            let clusterName = $( this ).text().trim() ;
            let relPkg = utils.getActiveProject() ;

            console.log( "\n\nshowSummaryView(): launching: " + clusterEditUrl
                    + " lifeEdit: " + lifeEdit + " clusterName: " + clusterName + " project: " + relPkg ) ;

            let includeTemplates = $( "#include-csap-templates-checkbox" ).is( ":checked" ) ;
            let params = {
                project: relPkg,
                lifeToEdit: lifeEdit,
                clusterName: clusterName,
                includeTemplates: includeTemplates
            }

            launchClusterEditor( params ) ;

            return false ;

        } ) ;



        $( ".addNewClusterButton" ).click( function () {
            console.log( "Adding cluster" ) ;
            alertify.prompt( "New service cluster", 'Enter the name', "your-new-cluster",
                    function ( evt, newClusterName ) {

                        let lifeEdit = $( "#lifeEdit" ).val() ;
                        let clusterName = $( this ).text().trim() ;
                        let relPkg = utils.getActiveProject() ;


                        let params = {
                            project: relPkg,
                            lifeToEdit: lifeEdit,
                            "newService": "newService",
                            clusterName: newClusterName
                        }

                        launchClusterEditor( params ) ;

                    },
                    function () {
                        console.log( "canceled" ) ;
                    }
            ) ;
            return false ;
        } ) ;
    }

    function applyClusterFilter() {

        clearTimeout( _clusterFilterTimer ) ;

        _clusterFilterTimer = setTimeout( function () {

            let filter = $clusterFilter.val() ;
            let $lifeTable = $( "#editLifeTable", $targetContainer ) ;



            console.log( "Need to filter: " + filter ) ;
            $( "tbody tr", $lifeTable ).each( function () {
                let numberMatches = 0 ;
                $( "a", $( this ) ).each( function () {
                    if ( $( this ).text().toLowerCase().contains( filter.toLowerCase() ) ) {
                        numberMatches += 1 ;
                        $( this ).addClass( "svcHighlight" ) ;
                    } else {
                        $( this ).removeClass( "svcHighlight" ) ;
                    }
                } ) ;
                $( "div.clusterHostName", $( this ) ).each( function () {
                    if ( $( this ).text().toLowerCase().contains( filter.toLowerCase() ) ) {
                        numberMatches += 1 ;
                        $( this ).addClass( "svcHighlight" ) ;
                    } else {
                        $( this ).removeClass( "svcHighlight" ) ;
                    }
                } ) ;

                console.log( "Filter matches: " + numberMatches + " filter.length: " + filter.length ) ;
                if ( numberMatches == 0 ) {
                    $( this ).hide() ;
                } else {
                    $( this ).show() ;
                }
            } ) ;
            
            
            if ( filter.length == 0 ) {
                $( ".svcHighlight", $lifeTable ).removeClass( "svcHighlight" ) ;
            }
            

        }, 200 ) ;


    }

    function launchServiceEditor( serviceName ) {
        console.log( "\n\nshowSummaryView(): launching: " + serviceEditUrl + " serviceName: " + serviceName ) ;


        let includeTemplates = $( "#include-csap-templates-checkbox" ).is( ":checked" ) ;

        let params = {
            project: utils.getActiveProject(),
            serviceName: serviceName,
            hostName: "*",
            includeTemplates: includeTemplates
        }

        utils.loading( "Loading service definition" ) ;
        $.get( serviceEditUrl,
                params,
                function ( dialogHtml ) {
                    serviceEdit.showServiceDialog( serviceName, dialogHtml ) ;
                    utils.loadingComplete() ;
                },
                'html' ) ;
    }

    function launchClusterEditor( params ) {

        console.log( `launchClusterEditor(): launching: ${ clusterEditUrl } params ${params} _lastLife: ${_lastEnvironmentSelected}` ) ;

        clusterEditor.setRefresh( _refreshFunction ) ;

        utils.loading( `Loading cluster...` ) ;
        $.get( clusterEditUrl,
                params,
                function ( htmlTemplate ) {
                    utils.loadingComplete() ;
                    clusterEditor.show( htmlTemplate ) ;
                }
        ,
                'html' ) ;
    }

    function registerLifeOperations() {

        $( ".addNewLifeButton" ).click( function () {
            //alertify.notify( "Adding life" );
            alertify.prompt( "Add a new runtime environment", 'Enviroment name must be lowercase, host compliant. Eg. dev, dev-1, test-2, lt, prod', "dev-sandbox-1",
                    function ( evt, newName ) {
                        console.log( 'newName: ' + newName ) ;
                        if ( newName.includes( "desktop" ) || newName.includes( "agent" ) || newName.includes( "desktop" ) ) {
                            alertify.csapWarning( "lifecycle name cannot be desktop, agent, or admin. Select another name" ) ;
                            return ;
                        }
                        let params = {
                            project: utils.getActiveProject(),
                            newName: newName,
                            lifeToEdit: $( ".lifeSelection" ).val(),
                            "operation": "add"
                        }

                        _lastEnvironmentSelected = newName ;

                        $.post( lifeUpdateUrl,
                                params,
                                showLifeOperationResults,
                                'json' ) ;
                    },
                    function () {
                        console.log( "canceled" ) ;
                    }
            ) ;
            return false ;

        } ) ;

        $( ".removeLifeButton" ).click( function () {
            alertify.confirm( "Life cycle Removal", 'Click ok to continue',
                    function ( evt, newName ) {
                        console.log( 'newName: ' + newName ) ;
                        let params = {
                            project: utils.getActiveProject(),
                            lifeToEdit: $( ".lifeSelection" ).val(),
                            "operation": "delete"
                        }
                        $( ".lifeSelection option[value='" + _lastEnvironmentSelected + "']" ).remove() ;
                        _lastEnvironmentSelected = $( ".lifeSelection" ).val() ;
                        $.post( lifeUpdateUrl,
                                params,
                                showLifeOperationResults,
                                'json' ) ;
                    },
                    function () {
                        console.log( "canceled" ) ;
                    }
            ) ;
            return false ;
        } ) ;

    }

    function showLifeOperationResults( updatesResult ) {

        console.log( "showLifeOperationResults()", updatesResult ) ;

        let $userMessage = validationHandler.processValidationResults( updatesResult.validationResults ) ;

        let $debugInfo = jQuery( '<div/>', {
            class: "debugInfo",
            text: "*details...." + JSON.stringify( updatesResult, null, "\t" )
        } ) ;

        let okFunction = function () {
            console.log( "Closed results" ) ;
            _refreshFunction() ;
        }

        $userMessage.append( $debugInfo ) ;
        if ( updatesResult.updatedHost ) {
            let $moreInfo = $( "#dialogResult" ).clone().css( "display", "block" ) ;
            $( ".noteAlt", $moreInfo ).text( updatesResult.updatedHost ) ;
            $userMessage.append( $moreInfo ) ;

        }


        if ( updatesResult.message ) {
            $userMessage.append( "<br/><br/>" + updatesResult.message ) ;
        }

        jsonForms.showUpateResponseDialog( "Life cycle Results", $userMessage.html(), okFunction ) ;

        if ( updatesResult.validationResults.success ) {
            console.log( "showLifeOperationResults() triggering definition reload" ) ;
            jsonForms.runUpdateFunction() ;
        }
    }
    ;
} ) ;