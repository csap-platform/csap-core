define( [ "browser/utils", "projects/env-editor/service-edit-config", "editor/validation-handler", "editor/json-forms" ], function ( utils, configuration, validationHandler, jsonForms ) {

    console.log( "Service Edit Module new loaded" ) ;
    let _resultPanel = "#serviceEditorResult" ;
    let _editorSelector = "#serviceEditor" ;

    let _containerNav = "#container-nav" ;
    let $containerTabs ;
    let _container = "body" ;
    let _definitionSelector = "#serviceJson" ;
    let _isShowIdWarning = true ;

    let _hostName = "*" ;
    let _service ;

    let _serviceJson = null ;
    let _jsonParseTimer = null ;
    let _isJsonEditorActive = false ;

    let _currentDialog = null ;
    let _dialogId = "serviceEditorDialog" ;

    let _refreshLifecycleFunction = null ;

    let $propFileContainer, $propName, $propLifecycle, $propExternal, $propModified, $propContents, $propSelect ;
    let $containerTab, $serviceEditor, $dialogHeader ;

//    let $propFileContainer = $( "#fileContainer" ) ;
//    let $propName = $( "#propFileName" ) ;
//    let $propLifecycle = $( "#propLifecycle" ) ;
//    let $propExternal = $( "#propExternal" ) ;
//    let $propModified = $( "#propModified" ) ;
//    let $propContents = $( "#propFileText" ) ;
//    let $propSelect = $( "#propertyFileSelect" ) ;



    let _propSelectTimer = null ;

    return {
        setRefresh: function ( refreshFunction ) {
            _refreshLifecycleFunction = refreshFunction ;
        },
        setSpecificHost: function ( specificHostName ) {
            _hostName = specificHostName ;
        },
        //
        showServiceDialog: function ( serviceNameField, editDialogHtml ) {
            //when testing standalone, tests without
            if ( !jsonForms.isDialogBuilt( _dialogId ) ) {
                // do it only once.
                _container = ".ajs-dialog "
                _resultPanel = _container + _resultPanel ;
                _editorSelector = _container + _editorSelector ;
                _definitionSelector = _container + _definitionSelector ;
            }
            refreshJquerySelectors() ;

            return showServiceDialog( serviceNameField, editDialogHtml ) ;
        },
        getServiceDefinition: function () {
            getServiceDefinition() ;
        },
        registerDialogButtons: function () {
            registerInputEvents() ;
        },
        updateServiceDefinition: function () {
            updateServiceDefinition( true ) ;
        },
        validateServiceDefinition: function () {
            updateServiceDefinition( false ) ;
        },
        configureForTest: function () {

            registerUiComponents() ;
            getServiceDefinition( serviceName ) ;
            jsonForms.registerOperations( updateServiceDefinition ) ;

        }
    }


    function refresh_layout() {
        console.log( `refresh_layout: isTemplate: ${isServiceTemplate()}` ) ;

        let readOnly = isServiceTemplate() ;
        if ( $propFileContainer
                && $propFileContainer.is( ":visible" ) ) {
            readOnly = false ;
        }
        jsonForms.resizeVisibleEditors(
                _definitionSelector,
                _container,
                _editorSelector,
                readOnly ) ;
    }


    function refreshJquerySelectors() {
        
        console.log(`refreshJquerySelectors()`) ;

        $serviceEditor = $( _editorSelector ) ;
        $containerTabs = $( _containerNav, $serviceEditor ) ;
        
        $dialogHeader = $( "#dialogOpsContainer", $serviceEditor.parent() ) ;

        $propFileContainer = $( "#fileContainer", $serviceEditor ) ;
        $propName = $( "#propFileName", $serviceEditor ) ;
        $propLifecycle = $( "#propLifecycle", $serviceEditor ) ;
        $propExternal = $( "#propExternal", $serviceEditor ) ;
        $propModified = $( "#propModified", $serviceEditor ) ;
        $propContents = $( "#propFileText", $serviceEditor ) ;
        $propSelect = $( "#propertyFileSelect", $serviceEditor ) ;
        $containerTab = $( "#tab-container-content", _container ) ;
    }

    function registerUiComponents() {

        console.log( "registerUiComponents(): registering tab events" ) ;

        refreshJquerySelectors() ;


        $( ".definition-source", $serviceEditor ).change( function () {
            setTimeout( function () {
                let profile = $( ".serviceContainer", _container ).val() ;
                update_viewable_sections( profile, false ) ;
                refresh_layout() ;
            }, 500 ) ;

            let definitionSource = $( this ).val() ;
            let showInput = "" ;
            if ( definitionSource === "copySource" ) {
                showInput = ".copySource-service-name" ;
            } else if ( definitionSource === "csap-templates" ) {
                showInput = ".path-to-template" ;
            }
            setTimeout( function () {
                $( showInput ).show() ;
            }, 600 ) ;


        } ) ;

        $( ".serviceContainer", $serviceEditor ).change( function () {

            let profile = $( this ).val() ;
            update_viewable_sections( profile, false ) ;

        } ) ;

        $( "#service-template-selection", $serviceEditor ).change( loadServiceTemplate ) ;

        $serviceEditor.tabs( {
            beforeActivate: function ( event, ui ) {

                if ( ui.oldTab.text().indexOf( "Editor" ) != -1 ) {
                    // refresh ui with edit changes.
                    console.log( "beforeActivate():  parsing serviceJson" ) ;
                    _serviceJson = JSON.parse( $( _definitionSelector ).val() ) ;
                    getServiceDefinitionSuccess( _serviceJson ) ;
                }

            },
            activate: function ( event, ui ) {
                console.log( "activate(): " + ui.newTab.text() ) ;
                rebind_display_controls() ;
                _isJsonEditorActive = false ;
                if ( ui.newTab.text().indexOf( "Editor" ) != -1 ) {
                    activateJsonEditor() ;
                }

                if ( ui.newTab.text().indexOf( "Performance" ) != -1 ) {
                    populatePerformanceTable() ;
                }


                if ( ui.newTab.text().indexOf( "Files" ) != -1 ) {
                    update_files_tab() ;
                }

                refresh_layout() ;

            }

        } ) ;

        registerInputEvents() ;

        console.log( `creating container tabs` ) ;
        $containerTabs.tabs( {
            activate: function ( event, ui ) {
                refresh_layout() ;
            } //,
            //disabled: [0, 1, 2, 3, 4, 5]
        } ) ;



        $( "select.dialogServiceSelect" ).selectmenu( {
            width: "200px",
            change: function () {
                console.log( "registerUiComponents editing service: " + $( this ).val() ) ;
//                serviceName = $( this ).val();
                getServiceDefinition( $( this ).val() ) ;
            }
        } ) ;

        $serviceEditor.show() ;
        // activateTab( "docker" );

    }

    function loadServiceTemplate() {

        console.log( "loadServiceTemplate" ) ;
        utils.loading( "loading service template" ) ;

        let templateName = $( this ).val() ;
        $( this ).val( "default" ) ;

        $.getJSON( SERVICE_DEF_URL + "/template", {
            "templateName": templateName
        } )
                .done( function ( templateDefinition ) {

                    utils.loadingComplete() ;

                    console.log( "templateDefinition.docker", templateDefinition.docker ) ;
                    if ( templateDefinition.server != undefined ) {
                        _serviceJson = templateDefinition ;
                    } else {
                        _serviceJson.docker = templateDefinition ;
                    }

                    getServiceDefinitionSuccess( _serviceJson ) ;
                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    $( ".serviceLoading" ).hide() ;
                    failedToGetService( "retrieving: docker template host: " + _hostName + " from: " + SERVICE_DEF_URL, errorThrown ) ;
                } ) ;
    }

    function activateTab( tabId ) {
        let tabIndex = $( 'li[data-tab="' + tabId + '"]' ).index() ;

        console.log( "Activating tab: " + tabIndex ) ;

        // $("#jmx" ).prop("checked", true) ;

        $serviceEditor.tabs( "option", "active", tabIndex ) ;

        return ;
    }


    function activateJsonEditor() {
        _isJsonEditorActive = true ;
        $( _definitionSelector ).val( JSON.stringify( _serviceJson, null, "\t" ) ) ;
    }


    function showServiceDialog( serviceNameField, editDialogHtml ) {

        jsonForms.showDialog(
                _dialogId,
                refresh_layout,
                editDialogHtml,
                updateServiceDefinition ) ;

        registerUiComponents() ;

        //configuration.defaultService().description = $( ".serviceDesc" ).val() ;
        //configuration.defaultService().docUrl = $( ".serviceHelp" ).val();

        let isAddMode = $( ".addDefButton" ).is( ":visible" ) ;
        if ( !isAddMode ) {
            getServiceDefinition( serviceNameField ) ;

        } else {
            $( ".serviceLoading" ).hide() ;
            getServiceDefinitionSuccess( configuration.defaultService() ) ;
        }



    }

    function getServiceDefinition( selectedService ) {

        console.log( `getServiceDefinition(): ${SERVICE_DEF_URL}  service: ${selectedService}` ) ;
        _service = selectedService ;

        utils.loading( "loading service template" ) ;
        $( ".serviceLoading" ).show() ;

        let paramObject = {
            project: utils.getActiveProject(),
            serviceName: selectedService,
            hostName: _hostName
        } ;
        //{(label='Definition Files',serviceName=${serviceName} )}
        $( ".open-service-files" ).off().click( function () {
            let serviceShortName = $( "#dialogServiceSelect" ).val() ;
            //alertify.closeAll() ;
            jsonForms.closeDialog() ;
            utils.launchServiceResources( serviceShortName ) ;
//            let commandUrl = baseUrl + 'file/FileManager?quickview=Definition Files: ' + serviceShortName + '&' +
//                    'fromFolder=__platform__/definition/resources/' + serviceShortName + "&" ;
//
//            openWindowSafely( commandUrl, "_blank" ) ;
            return false ;
        } )
        
        $propFileContainer.hide() ;

        $.getJSON( SERVICE_DEF_URL, paramObject )

                .done( function ( serviceJson ) {

                    utils.loadingComplete() ;
                    $( ".serviceLoading" ).hide() ;
                    getServiceDefinitionSuccess( serviceJson ) ;
                    update_files_tab() ;
                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    utils.loadingComplete() ;
                    $( ".serviceLoading" ).hide() ;
                    failedToGetService( "retrieving: " + selectedService + " host: " + _hostName + " from: " + SERVICE_DEF_URL, errorThrown ) ;
                } ) ;
    }


    function getServiceDefinitionSuccess( serviceJson ) {
        _serviceJson = serviceJson ;

        if ( _serviceJson.message ) {
            alertify.csapWarning( _serviceJson.message ) ;
        }

        if ( _serviceJson.lastModifiedBy == undefined ) {
            $( ".lastModified" ).hide() ;

        } else {
            $( ".lastModified .author" ).text( _serviceJson.lastModifiedBy ) ;

        }

        $( _definitionSelector ).val( JSON.stringify( serviceJson, null, "\t" ) ) ;

        // update form values

        jsonForms.loadValues( _editorSelector, serviceJson, configuration.defaultFields() ) ;

        // console.log(`_serviceJson.docker["deployment-files-use"]: ${_serviceJson.docker["deployment-files-use"]}`) ;
        let isSpec = _serviceJson.docker && _serviceJson.docker["deployment-files-use"] &&
                _serviceJson.docker["deployment-files-use"] == "true" ;
        update_viewable_sections( _serviceJson.server, isSpec ) ;


        refresh_layout() ;

        if ( $( "#jsonEditor" ).is( ":visible" ) ) {
            activateJsonEditor() ;
        }
        if ( $( "#performance" ).is( ":visible" ) ) {
            populatePerformanceTable() ;
        }


    }

    function rebind_display_controls() {

        console.log( `rebind_display_controls - container ${$containerTab.attr( "id" )} ` ) ;
        let $useSpecLabel = $( "#container-use-spec", $containerTab ) ;
        $( "select", $useSpecLabel ).off().change( function () {
            jsonForms.updateDefinition( _serviceJson, $( this ) ) ;
            let isUseSpecOnly = $( this ).val() ;
            console.log( `useSpec: ${isUseSpecOnly}, containerTab: '${ $containerTab.attr( "id" ) }'` ) ;

            if ( isUseSpecOnly == "true" ) {
                containerTabDisable() ;
                containerTabEnable( "ctabs-specification" ) ;
                containerTabActivate( "ctabs-specification" ) ;

                $( ".service-locator-items" ).fadeTo( 500, 1.0 ) ;
//                $( ".use-deployment-file-panel" ).fadeTo( 500, 1.0 );
                // $( ".primary", $containerTab ).hide();
                //$( ".use-deployment-file-panel", $containerTab ).show();
                //$( ".ctab-spec-tab", $containerTab ).show();
            } else {
                containerTabEnable() ;
                $( ".service-locator-items" ).fadeTo( 500, 0.2 ) ;
//                $( ".use-deployment-file-panel" ).fadeTo( 500, 0.2 );
                // $( ".primary", $containerTab ).show();
                //$( ".use-deployment-file-panel", $containerTab ).hide();
                // $( ".ctab-spec-tab", $containerTab ).hide();
            }

            refresh_layout() ;
        } ) ;

        $( "button.code-samples" ).off().click( function () {
            let type = $( this ).data( "type" ) ;
            let codeSample = configuration.help()[type] ;
            alertify.csapInfo( JSON.stringify( codeSample, null, "  " ) ) ;
        } ) ;
    }


    //$containerTabs.tabs( "disable", "#ctabs-definition" ) ;
    // $containerTabs.tabs( "enable", 1 ) ;
    function containerTabEnable( id ) {
        //console.log(`enabling: ${id} `, $containerTabs) ;
        $containerTabs.tabs( "enable", id ) ;
    }

    function containerTabDisable( id ) {
        $containerTabs.tabs( "disable", id ) ;
    }

    function containerTabActivate( tabId ) {
        //let tabIndex = $( "." + tabId ).index();
        let tabIndex = $( 'a[href="#' + tabId + '"]' ).parent().index() ;
        console.log( `containerTabActivate() tab: id: '${tabId}' index: ${tabIndex}` ) ;
        $containerTabs.tabs( "option", "active", tabIndex ) ;

        return ;
    }

    function update_viewable_sections( serverType, isSpecOnlyDeploy ) {

        let isAddMode = $( ".addDefButton" ).is( ":visible" ) ;

        isSpecOnlyDeploy = ( isSpecOnlyDeploy == true ) ;

        if ( isSpecOnlyDeploy ) {
            console.log( `using deployment specification` ) ;
        }

        $( ".service-tab-container" ).show() ;

        containerTabEnable(  ) ;
        $( "#container-use-spec" ).show() ;
        $( ".service-deploy-files" ).fadeTo( 500, 1.0 ) ;
        $( ".service-locator-items" ).fadeTo( 500, 1.0 ) ;
        // 
        // $( ".primary", $containerTab ).show();
        //$( ".use-deployment-file-panel", $containerTab ).hide() ;
        //$(".ctab-spec-tab", $containerTab).hide() ;
        $( ".deployTab" ).show() ;
        $( "#dockerContainerSelect" ).show() ;

        let $useSpecLabel = $( "#container-use-spec", $containerTab ) ;
        $useSpecLabel.show() ;


        console.log( `update_viewable_sections: 2 serverType: ${serverType}, spec: ${ isSpecOnlyDeploy }, containerTab: ${$containerTab.attr( "id" )}` ) ;
        if ( isAddMode ) {

            $( ".package-tomcat" ).show() ;
//            $( ".jeeOptions" ).show();
//            $( ".package-os" ).show();
//            $( ".osOptions" ).show();
            $( ".use-deployment-file-panel", $containerTab ).show() ;

        } else if ( serverType.contains( "csap-api" )
                || serverType.contains( "os" )
                || serverType.contains( "docker" ) ) {

            if ( isSpecOnlyDeploy ) {
                containerTabDisable() ;
                containerTabEnable( "ctabs-specification" ) ;
                //$( ".use-deployment-file-panel" ).fadeTo( 500, 1.0 );
            } else {
                $( ".service-locator-items" ).fadeTo( 500, 0.2 ) ;
                //$( ".use-deployment-file-panel" ).fadeTo( 500, 0.2 );
                containerTabActivate( "ctabs-definition" ) ;
            }


            $( ".package-tomcat" ).hide() ;
//            $( ".jeeOptions" ).hide();
//            $( ".package-os" ).show();
//            $( ".osOptions" ).show();

            if ( serverType.contains( "os" )
                    || isSpecOnlyDeploy ) {
                console.log( `OS or specification deployment: ${isSpecOnlyDeploy}` ) ;

                containerTabEnable( "#ctabs-specification" ) ;

                //$( ".primary", $containerTab ).hide();
                //$( ".use-deployment-file-panel", $containerTab ).show() ;
                //$(".ctab-spec-tab", $containerTab).show() ;

                if ( serverType.contains( "os" ) ) {
                    $( "#container-use-spec" ).hide() ;
                    containerTabDisable() ;
                    containerTabEnable( "ctabs-specification" ) ;
                    containerTabActivate( "ctabs-specification" ) ;
//                    $( ".use-deployment-file-panel" ).fadeTo( 500, 1.0 );
                    $( ".service-locator-items" ).fadeTo( 500, 1.0 ) ;
                    $( ".service-deploy-files" ).fadeTo( 500, 0.2 ) ; // .hide();
                }
            }
            if ( serverType.contains( "csap-api" ) ) {
                $( ".service-tab-container" ).hide() ;
            }
        } else {
            containerTabDisable( "ctabs-specification" ) ;
            containerTabDisable( "ctabs-kubernetes" ) ;
            containerTabActivate( "ctabs-definition" ) ;
            $( ".package-tomcat" ).show() ;
            $useSpecLabel.hide() ;
            if ( serverType.contains( "Boot" ) ) {
                $( ".tomcatWar" ).hide() ;
                $( ".bootTomcat" ).show() ;
            } else {
                $( ".tomcatWar" ).show() ;
                $( ".bootTomcat" ).hide() ;
            }
            $( ".jeeOptions" ).show() ;
//            $( ".package-os" ).hide();
//            $( ".osOptions" ).hide();
        }


        if ( serverType.contains( "docker" ) || serverType.contains( "os" ) ) {
//            $( ".deployTab" ).hide() ;
            if ( serverType.contains( "docker" ) ) {
                $( "#dockerContainerSelect" ).hide() ;
            }
        }



//        $("input").css("color", "red") ;
        if ( isServiceTemplate() ) {
            $( ".deleteDefButton" ).text( "Delete From Application.json" ) ;
            $( ".copyDefButton" ).hide() ;
            $( ".renameDefButton" ).hide() ;
            $( ".copySource-service-name" ).hide() ;
            $( ".path-to-template" ).hide() ;
            if ( _serviceJson && _serviceJson.copySource ) {
                $( ".copySource-service-name" ).show() ;
            }
            if ( _serviceJson && _serviceJson["path-to-template"] ) {
                $( ".path-to-template" ).show() ;
            }

            console.log( `Service Template: Disabling inputs: ${_editorSelector} ` ) ;
            $( "input", $serviceEditor )
                    .not( "#props input" )
                    .not( ".copySource-service-name" )
                    .prop( "disabled", true ) ;

            // allow code folding to occure
            $( ".editor-toggle-last" ).prop( "disabled", false ) ;
        } else {
            $( ".deleteDefButton" ).text( "Delete (+cluster)" ) ;
            ;
            $( ".copyDefButton" ).show() ;
            $( ".renameDefButton" ).show() ;
            $( ".copySource-service-name" ).hide() ;
            $( ".path-to-template" ).hide() ;
            $( "input", $serviceEditor ).not( "#props input" )
                    .prop( "disabled", false ) ;
        }



        if ( _serviceJson.javaWarnings == undefined ) {
            console.log( "getServiceDefinitionSuccess(): javaWarnings Hiding element: " + $( ".javaWarnings" ).attr( "id" ) ) ;
            $( ".javaWarnings" ).hide() ;
        }

        // $( ".container-use-spec select" ).trigger("change") ;

    }

    function isServiceTemplate() {
        return  _serviceJson && _serviceJson.definitionSource
                && (
                        ( _serviceJson.definitionSource === "csap-templates" )
                        || ( _serviceJson.definitionSource === "copySource" )
                        ) ;
    }


    function getDefinition() {
        console.log( "getDefinition()" ) ;
        return _serviceJson ;
    }

    function getPropertyFileCount() {
        if ( _serviceJson.files ) {
            return _serviceJson.files.length ;
        }
        return 0 ;
    }
    function pad( targetString, paddingArray, isPadLeft ) {

        //console.log("targetString.length", targetString.length, "paddingArray.length", paddingArray.length )
        if ( targetString.length > paddingArray.length )
            return targetString ;

        if ( typeof targetString === 'undefined' )
            return paddingArray ;
        if ( isPadLeft ) {
            return ( paddingArray + targetString ).slice( -paddingArray.length ) ;
        } else {
            return ( targetString + paddingArray ).substring( 0, paddingArray.length ) ;
        }
    }
    function getPropertyKey( life, name, addPadding ) {
        //console.log( "getPropertyKey", life, name ) ;

        if ( !addPadding ) {
            return life + "-" + name ;
        }
        let padding = Array( addPadding ).join( ' ' ) ;
        let paddedString = pad( life + ":", padding ) + name ;
        let propertyKey = paddedString.replace( / /g, '&nbsp;' ) ;
        
        console.debug( `getPropertyKey() life: '${life}', name: ${ name }  result: ${ propertyKey }` ) ;
        
        return propertyKey ;
    }

    function update_files_tab( source, isNewItem = false ) {

        if ( _propSelectTimer ) {
            clearTimeout( _propSelectTimer ) ;
        }

        function delay_tab_configure() {
            console.groupCollapsed( `update_files_tab()` ) ;
            if ( Array.isArray( _serviceJson.files ) ) {

                
                $propSelect.empty() ;
                console.log( `file count: ${ _serviceJson.files.length }` ) ;

                let lastItemAdded = "" ;
                for ( let serviceFile of _serviceJson.files ) {
                    lastItemAdded = getPropertyKey( serviceFile.lifecycle, serviceFile.name ) ;
                    console.log( `adding option: '${lastItemAdded}'` ) ;
                    jQuery( '<option/>', {
                        html: getPropertyKey( serviceFile.lifecycle, serviceFile.name, 10 ),
                        value: lastItemAdded
                    } ).appendTo( $propSelect ) ;
                }
                
                $propSelect.sortSelect() ;

                if ( isNewItem ) {
                    console.log(`new item`) ;
                    $propSelect.val( lastItemAdded ) ;
                    $propSelect.trigger( "change" ) ;
                } else {
                    let defaultItem = getPropertyKey( $propLifecycle.val(), $propName.val() ) ;
                    console.log( `Selecting '${defaultItem}' `) ;
                    //$propSelect.val( defaultItem ) ;
                }

                

                if ( _serviceJson.files.length > 0 ) {

                    if ( $propFileContainer.css( "display" ) === "none" ) {
                        console.log( "Showing file contents" ) ;
                        $propFileContainer.show() ;
                        $propSelect.trigger( "change" ) ;
                    }
                }

            } else {
                $propSelect.empty() ;
                jQuery( '<option/>', {
                    text: "no files"
                } ).appendTo( $propSelect ) ;
            }
            console.groupEnd() ;
        }
        _propSelectTimer = setTimeout( delay_tab_configure, 500 ) ;
    }

    function loadShellTemplate( templateName ) {


        console.log( "templateName", templateName ) ;

        $.get( baseUrl + "os/command/template/" + templateName,
                function ( text ) {
                    //console.log( text ) ;
                    $propName.val( templateName ) ;
                    $propName.trigger( "change" ) ;

                    $propContents.val( text ) ;
                    $propContents.trigger( "change" ) ;

                    jsonForms.resizeVisibleEditors( _definitionSelector, _container, _editorSelector ) ;

                },
                'text'
                ) ;

    }

    function addServiceFile( $source) {

        console.log( "Adding file" ) ;

        let fileCount = getPropertyFileCount() ;
        $propFileContainer.show() ;

        if ( !_serviceJson.files ) {
            _serviceJson.files = new Array() ;
        }

        let newFile = new Object() ;
        _serviceJson.files.push( newFile ) ;
        newFile.name = "fileName" + fileCount ;
        let contentLines = new Array() ;
        contentLines.push( "# updated with your content" + fileCount ) ;
        newFile.content = contentLines ;
        newFile.lifecycle = "common" ;
        newFile.external = true ;
        newFile.newFile = true ;

        update_files_tab( $source, true ) ;

        refresh_layout() ;


    }

    function registerFileEvents() {

        console.log( "registering file events" ) ;

        $( "#load-file-template-select", $serviceEditor ).change( function () {

            let templateName = $( this ).val() ;
            addServiceFile( $(this) ) ;
            
            setTimeout( function () {

                loadShellTemplate( templateName ) ;
                $( "#load-file-template-select" ).val( "default" ) ;

            }, 100 ) ;
        } )


        $propName.change( update_files_tab ) ;
        $propLifecycle.change( update_files_tab ) ;

        $( "#service-delete-file-button" ).click( function () {

            console.log( `Deleting file, from files.length: ${_serviceJson.files.length}` )
            for ( let serviceFile of _serviceJson.files ) {

                let currentFile = getPropertyKey( serviceFile.lifecycle, serviceFile.name ) ;
                if ( $propSelect.val() == currentFile ) {
                    serviceFile.deleteFile = true ;
                    //serviceFile.content ="File is marked for delete"
                    $propContents.val( "File is marked for delete" ) ;
                    // _serviceJson.files.splice( i, 1 );
                }
            }
            refresh_layout() ;
            //$propName.val( "" );
            //syncPropertyFileSelect();
            //$propFileContainer.hide();
        } ) ;

        $propContents.change( function () {
            
            console.log( `service file content updated` ) ;
            if ( _serviceJson.files
                && $propContents.data( "init-load" ) !== "true" ) {
                _serviceJson.files[ $propContents.data( "file-index" ) ].contentUpdated = true ;
            }
            
        } ) ;

        $propSelect.off().change( function () {
            console.log( `file selection modified` ) ;
            for ( let fileIndex = 0 ; fileIndex < _serviceJson.files.length ; fileIndex++ ) {
                let propertyFile = _serviceJson.files[fileIndex] ;

                let propertyFullName = getPropertyKey(
                        _serviceJson.files[fileIndex].lifecycle,
                        _serviceJson.files[fileIndex].name ) ;

                if ( propertyFullName == $propSelect.val() ) {

                    // update json-forms data for extraction to json definition
                    $propName.val( propertyFile.name ) ;
                    $propName.data( "path", "files[" + fileIndex + "].name" ) ;

                    $propLifecycle.val( propertyFile.lifecycle ) ;
                    $propLifecycle.data( "path", "files[" + fileIndex + "].lifecycle" ) ;

                    $propExternal.val( propertyFile.external ) ;
                    $propExternal.data( "path", "files[" + fileIndex + "].external" ) ;

                    $propModified.val( propertyFile.contentUpdated ) ;
                    $propModified.data( "path", "files[" + fileIndex + "].contentUpdated" ) ;



                    $propContents.data( "path", "files[" + fileIndex + "].content" ) ;

                    // take array data and convert for UI textarea
                    let textLines = "" ;

                    if ( propertyFile.content ) {
                        for ( let line_in_file of  propertyFile.content ) {
                            textLines += line_in_file + "\n" ;
                        }
                        loadResourceContents( textLines, fileIndex, propertyFullName )

                    } else {
                        utils.loading( `loading resource: ${propertyFile.name}` ) ;
                        loadResourceFile( propertyFile.name, propertyFile.lifecycle,
                                fileIndex, propertyFullName ) ;
                    }

                }
            }
            //$propFileContainer.show() ;
        } ) ;
    }

    function loadResourceContents( textLines, fileIndex, propertyFullName ) {
        $propContents.val( textLines ) ;

        $propContents.data( "file-index", fileIndex ) ;
        $propContents.data( "init-load", "true" ) ;
        $propContents.trigger( "change" ) ;
        jsonForms.assign_editor_mode( $propContents, propertyFullName ) ;
        refresh_layout() ;

        setTimeout( () => {
            // mark subsequent changes
            $propContents.data( "init-load", "false" ) ;
        }, 500 ) ;

    }

    function loadResourceFile( fileName, environment, fileIndex, propertyFullName ) {

        $.getJSON( SERVICE_DEF_URL + "/resource", {
            project: utils.getActiveProject(),
            serviceName: _service,
            hostName: _hostName,
            "fileName": fileName,
            "environment": environment
        } )
                .done( function ( fileContents ) {

                    //console.log(`loadResourceFile`, fileContents) ;
                    if ( fileContents ) {

                        let textLines = "" ;
                        for ( let line_in_file of  fileContents ) {
                            textLines += line_in_file + "\n" ;
                        }
                        loadResourceContents( textLines, fileIndex, propertyFullName ) ;
                    } else {
                        console.log( `no contents found` ) ;
                    }
                    utils.loadingComplete() ;

                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    alertify.csapWarning( `Failed to load ${name} in env ${ environment}` ) ;
                    utils.loadingComplete() ;

                } ) ;
    }

// Need to register events after dialog is loaded
    function registerInputEvents() {
        
        refreshJquerySelectors() ;
        
        registerFileEvents() ;

        $( "input[name=updateOp]" ).click( function () {
            let operation = $( "input[name=updateOp]:checked" ).val() ;
            if ( operation == "add" || operation == "rename" ) {
                $( "#opsNewName" ).show() ;
            } else {
                $( "#opsNewName" ).hide() ;
            }
        } ) ;

        //configureJsonEditors();
        jsonForms.configureJsonEditors( getDefinition, _container, _editorSelector, _definitionSelector ) ;

        console.log( "registerInputEvents(): register events" ) ;
        $( ".toggleAppCollect" ).click( function () {
            console.log( "registerInputEvents toggling display" ) ;
            $( ".appCollectToggle" ).toggle() ;
            if ( $( "table.appCollect" ).is( ":visible" ) ) {
                //parseAndUpdateJsonEdits( $( ".appCollectToggle textarea" ) );
                jsonForms.parseAndUpdateJsonEdits( _serviceJson, $( ".appCollectToggle textarea" ) )
                populatePerformanceTable() ;
                $( ".toggleAppCollect" ).text( "Show Editor" ) ;
            } else {
                // update text contents
                getServiceDefinitionSuccess( _serviceJson ) ;
                // resizeVisibleEditors();

                refresh_layout() ;
                $( ".toggleAppCollect" ).text( "Show Summary" ) ;
            }
        } ) ;

        // set default placeHolder on Alerts tab
        $( " #alerts label.alerts input", $serviceEditor ).attr( "placeholder", " *" ) ;


        $( " input," + " select", $serviceEditor ).change( function () {
            jsonForms.updateDefinition( _serviceJson, $( this ) ) ;
        } ) ;

    }


    function getValueOrDefault( value ) {
        if ( value == undefined || value == "" ) {
            return "---"
        }
        return value ;
    }

    function populatePerformanceTable() {
        console.log( "populatePerformanceTable Refresh" ) ;
        let $tbody = $( ".appCollect tbody" ) ;
        $tbody.empty() ;
        let numRows = 0 ;
        for ( let metricId in _serviceJson.performance ) {
            numRows++ ;
            let metricData = _serviceJson.performance[ metricId ] ;

            let $metricRow = jQuery( '<tr/>', { 'data-order': metricId } ) ;
            $metricRow.appendTo( $tbody ) ;



            let $labelCell = jQuery( '<td/>', { } ).appendTo( $metricRow ) ;
            $labelCell.append( jQuery( '<button/>', {
                title: "Click/Drag to re-order", class: "csap-icon csap-list moveRow"
            } ) ) ;
            $labelCell.append( jQuery( '<button/>', {
                title: "Add a new row", class: "csap-icon csap-edit newRow"
            } ) ) ;
            $labelCell.append( jQuery( '<button/>', {
                title: "Delete row", class: "csap-icon csap-trash deleteRow"
            } ) ) ;

            let $titleDiv = jQuery( '<div/>', {
                class: "tedit",
                "data-path": "performance." + metricId + ".title",
                text: getValueOrDefault( metricData.title )
            } ).appendTo( $labelCell ) ;

            let $idCell = jQuery( '<td/>', { } ).appendTo( $metricRow ) ;
            let $idDiv = jQuery( '<div/>', {
                class: "tedit",
                title: "WARNING: renaming ID looses all history. Only alpha chars are permitted",
                "data-id": metricId,
                text: metricId
            } ).appendTo( $idCell ) ;


            let $typeCell = jQuery( '<td/>', { class: "" } ) ;
            $typeCell.appendTo( $metricRow ) ;

            let type = getMetricType( metricId, metricData ) ;
            $typeCell.append( buildSourceCell( type, metricId, metricData ) ) ;

            let $collectCell = jQuery( '<td/>', { } ).appendTo( $metricRow ) ;

            buildCollectorDiv( metricId, type, metricData )
                    .appendTo( $collectCell ) ;




            let $maxCell = jQuery( '<td/>', { } ).appendTo( $metricRow ) ;
            let $maxDiv = jQuery( '<div/>', {
                class: "tedit",
                "data-path": "performance." + metricId + ".max",
                text: getValueOrDefault( metricData.max )
            } ).appendTo( $maxCell ) ;


        }

        registerPerformanceTableEvents( $tbody ) ;

        if ( numRows == 0 ) {
            let $metricRow = jQuery( '<tr/>', { } ) ;
            $metricRow.appendTo( $tbody ) ;

            let $missingMsg = jQuery( '<td/>', {
                class: "",
                colspan: 99,
                html: $( ".missingMetrics" ).html()
            } ).appendTo( $metricRow ) ;
            $missingMsg
                    .css( "max-width", "50em" )
                    .css( "word-break", "normal" )
                    .css( "font-size", "1.5em" ) ;
        }
    }

    function registerPerformanceTableEvents( $tbody ) {
        $tbody.sortable( {
            handle: "button.moveRow",
            cancel: '', //enables buttons for handles
            update: function ( event, ui ) {
                console.log( "reorderd rows: ", ui ) ;
                //saveLayout( $graphContainer, $plotContainer );
                let currentOrderItems = _serviceJson.performance ;
                //let currentItems = definitionContainer[ attribute ];
                let updatedItemIndexOrder = new Array() ;
                $( "tr", $tbody ).each( function () {
                    updatedItemIndexOrder.push( $( this ).data( "order" ) ) ;
                } ) ;
                console.log( "row order: ", updatedItemIndexOrder ) ;
                let itemsInNewOrder = new Object() ;
                for ( let i = 0 ; i < updatedItemIndexOrder.length ; i++ ) {
                    let field = updatedItemIndexOrder[i] ;
                    itemsInNewOrder[ field ] = currentOrderItems[ field ] ;
                }
//				;
                _serviceJson.performance = itemsInNewOrder ;

                //console.log( "itemsInNewOrder: ", itemsInNewOrder );
            }
        } ) ;

        $( ".deleteRow", $tbody ).click( function () {
            let attributeToDelete = $( this ).parent().parent().data( "order" ) ;

            let currentItems = _serviceJson.performance ;
            let updatedItems = new Object() ;
            for ( let attribute in currentItems ) {
                if ( attribute != attributeToDelete )
                    updatedItems[attribute] = currentItems[attribute] ;
            }

            _serviceJson.performance = updatedItems ;

            console.log( "updatedItems: ", updatedItems ) ;
            populatePerformanceTable() ;
        } ) ;

        $( ".newRow", $tbody ).click( function () {
            let attributeToDuplicate = $( this ).parent().parent().data( "order" ) ;

            let currentItems = _serviceJson.performance ;
            let updatedItems = new Object() ;
            for ( let attribute in currentItems ) {
                updatedItems[attribute] = currentItems[attribute] ;
                if ( attribute === attributeToDuplicate ) {
                    let newObject = jQuery.extend( { }, currentItems[attribute] ) ;
                    newObject.title = "enter new label"
                    updatedItems["updateThisId"] = newObject ;
                }
            }

            _serviceJson.performance = updatedItems ;

            console.log( "updatedItems: ", updatedItems ) ;
            populatePerformanceTable() ;
        } ) ;


        $( ".tedit", $tbody ).click( function () {
            let content = $( this ).text() ;
            if ( content == "---" )
                content = "" ;
            let actual = $( this ).data( "actual" ) ;
            if ( actual )
                content = actual ;
            let path = $( this ).data( "path" ) ;
            let id = $( this ).data( "id" ) ;
            let type = $( this ).data( "type" ) ;
            let truefalse = $( this ).data( "truefalse" ) ;
            if ( truefalse ) {
                if ( content == "false" ) {
                    $( this ).text( "true" ) ;
                } else {
                    $( this ).text( "false" ) ;
                }
                jsonForms.updateDefinition( _serviceJson, $( this ) ) ;
                return ;
            }
            $( this ).off() ;
            $( this ).empty() ;

            let $editValueContainer ;

            if ( id && _isShowIdWarning && !type ) {
                _isShowIdWarning = false ;
                alertify.alert( "Modifying the collection id will remove correlation with previously collected results" ) ;
            }
            if ( type ) {
                $editValueContainer = jQuery( '<select/>', {
                    class: "",
                    "data-type": type,
                    "data-id": id,
                    "data-path": path
                } ) ;
                let options = $( "#serviceTemplates select.attType" ).html() ;
                $editValueContainer.html( options ) ;
                $editValueContainer.sortSelect() ;
            } else {
                let $editValueContainer = jQuery( '<input/>', {
                    class: "",
                    "data-id": id,
                    "data-path": path,
                    value: content
                } ) ;
            }

            $editValueContainer.change( function () {
                let oldId = $( this ).data( "id" ) ;
                let oldType = $( this ).data( "type" ) ;
                //console.log("Updating performance:", $( this ).data("id")) ;
                if ( oldType ) {
                    // new ID needs to be used
                    let newType = $( this ).val() ;
                    // updating field value
                    console.log( "id: ", oldId, " update collection type from: ", oldType, " to: ", newType ) ;
                    let metricToUpdate = _serviceJson.performance[oldId] ;
                    let setting = metricToUpdate[oldType] ;
                    delete metricToUpdate[oldType] ;
                    metricToUpdate[newType] = setting ;
                    populatePerformanceTable() ;

                } else if ( oldId ) {
                    // new ID needs to be used
                    let newId = $( this ).val() ;
                    console.log( "Updating id: ", oldId, " to: ", newId ) ;

                    let currentItems = _serviceJson.performance ;
                    let updatedItems = new Object() ;
                    for ( let attribute in currentItems ) {

                        if ( attribute == oldId ) {
                            let newObject = jQuery.extend( { }, currentItems[attribute] ) ;
                            updatedItems[newId] = newObject ;
                        } else {
                            updatedItems[attribute] = currentItems[attribute] ;
                        }
                    }

                    _serviceJson.performance = updatedItems ;

                    console.log( "updatedItems: ", updatedItems ) ;
                    populatePerformanceTable() ;

                } else {
                    // updating field value
                    jsonForms.updateDefinition( _serviceJson, $( this ) ) ;
                }
            } ) ;
            $( this ).append( $editValueContainer ) ;

        } ) ;
    }
    function getMetricType( metricId, metricData ) {
        if ( metricData.mbean )
            return "mbean" ;
        if ( metricId == "config" )
            return "config" ;

        return "http" ;
    }

    function buildSourceCell( typeText, metricId, metricData ) {

        let $sourceDiv = jQuery( '<div/>', { class: "" } ) ;

        let $typeSelect = $( "#serviceTemplates select.attType" ) ;
        $typeSelect.val( typeText ) ;
        let typeLabel = typeText ;
        //console.log("trendType",  $("#settingsTemplates select.trendType").val()) ;
        if ( $typeSelect.val() != "" ) {
            let optionText = $( "option:selected", $typeSelect ).text() ;
            if ( optionText.length > 0 ) {
                typeLabel = optionText ;
            }
        }

        let $idDiv = jQuery( '<div/>', {
            class: "tedit",
            title: "Use browser or jvisualvm to confirm",
            "data-type": typeText,
            "data-id": metricId,
            text: typeLabel
        } ).appendTo( $sourceDiv ) ;


        if ( metricData.divideBy ) {
            let divideLabel = getValueOrDefault( metricData.divideBy ) ;
            if ( metricData.divideBy && metricData.divideBy == "1000000" ) {
                divideLabel = "(ms)" ;
            }
            let $divideBy = jQuery( '<div/>', {
                class: "tedit",
                title: "Divide collected result to simplify reporting. For per second, specify 'interval'.",
                "data-actual": getValueOrDefault( metricData.divideBy ),
                "data-path": "performance." + metricId + ".divideBy",
                text: divideLabel
            } ).appendTo( $sourceDiv ) ;
        }

        let customizations = "" ;

        if ( metricData.decimals )
            customizations += ".*(" + metricData.decimals + ")" ;

        // console.log("metricData.isHourlyAverage: " + metricData.isHourlyAverage) ;
        if ( metricData.reportRate ) {
            jQuery( '<div/>', { text: metricData.reportRate } )
                    .appendTo( $sourceDiv ) ;
        }

        $sourceDiv.append( customizations ) ;
        return $sourceDiv ;
    }

    function buildCollectorDiv( metricId, type, metricData ) {

        console.log( "buildCollectorDiv() type: ", type ) ;

        let $collectorDiv = jQuery( '<div/>', { } ) ;

        if ( type == "mbean" ) {

            let $mbean = jQuery( '<div/>', {
                class: "tedit",
                "data-path": "performance." + metricId + ".mbean",
                text: metricData[ type ]
            } ).appendTo( $collectorDiv ) ;

            let $attDiv = jQuery( '<div/>', {
                html: "attribute: "
            } ).css( "padding-top", "0.5em" ).appendTo( $collectorDiv ) ;


            jQuery( '<span/>', {
                class: "tedit",
                "data-path": "performance." + metricId + ".attribute",
                text: metricData.attribute
            } ).appendTo( $attDiv ) ;


            let $ignoreSpan = jQuery( '<span/>', {
                html: "ignore errors: ",
                title: "set to true to ignore collection errors for alerts"
            } ).css( "float", "right" ).css( "margin-left", "10px" ) ;
            $ignoreSpan.appendTo( $attDiv ) ;

            let ignoreErrors = metricData.ignoreErrors ;
            if ( !ignoreErrors )
                ignoreErrors = "false" ;
            jQuery( '<span/>', {
                class: "tedit",
                "data-path": "performance." + metricId + ".ignoreErrors",
                "data-truefalse": true,
                text: ignoreErrors
            } ).appendTo( $ignoreSpan ) ;


            $attDiv.append( buildDeltaInput( metricId, metricData ) ) ;

        } else if ( type == "http" ) {

            let $container = jQuery( '<div/>', {
            } ).appendTo( $collectorDiv ) ;

            jQuery( '<span/>', {
                class: "tedit",
                "data-path": "performance." + metricId + ".attribute",
                text: metricData.attribute
            } ).appendTo( $container ) ;

            $container.append( buildDeltaInput( metricId, metricData ) ) ;


        } else if ( type == "config" ) {

            $collectorDiv.append( "Collect URL:" ) ;

            let collectUrl = metricData.httpCollectionUrl ;
            let collectPath = "performance." + metricId + ".httpCollectionUrl" ;
            if ( !collectUrl ) {
                collectUrl = metricData.csapMicroUrl ;
                collectPath = "performance." + metricId + ".csapMicroUrl" ;
            }
            jQuery( '<span/>', {
                class: "tedit",
                "data-path": collectPath,
                text: collectUrl
            } ).css( "margin-left", "1em" ).appendTo( $collectorDiv ) ;

            let $matchDiv = jQuery( '<div/>', {
                html: "Match Using: "
            } ).appendTo( $collectorDiv ) ;

            jQuery( '<span/>', {
                class: "tedit",
                "data-path": "performance." + metricId + ".patternMatch",
                text: metricData.patternMatch
            } ).css( "margin-left", "1em" ).appendTo( $matchDiv ) ;

            if ( metricData.user ) {
                let $credDiv = jQuery( '<div/>', {
                    html: "Credentials: "
                } ).appendTo( $collectorDiv ) ;


                jQuery( '<span/>', {
                    class: "tedit",
                    "data-path": "performance." + metricId + ".user",
                    text: metricData.user
                } ).css( "margin-left", "1em" ).appendTo( $credDiv ) ;

                jQuery( '<span/>', {
                    class: "tedit",
                    "data-path": "performance." + metricId + ".pass",
                    text: metricData.pass
                } ).css( "margin-left", "1em" ).appendTo( $credDiv ) ;
            }


        }

        return $collectorDiv ;
    }

    function buildDeltaInput( metricId, metricData, isSimon ) {

        console.log( "metricData:", metricData ) ;
        let isDelta = false ;
        if ( isSimon ) {
            isDelta = true ;
        }
        if ( metricData.delta != undefined ) {
            if ( metricData.delta == "delta" ) {
                isDelta = true ;
            } else {
                isDelta = metricData.delta ;
            }
        }
        let $deltaDiv = jQuery( '<span/>', {
            text: "delta: ",
            title: "set to true to record the difference between subsequent collections"
        } ).css( "margin-left", "1em" ).css( "float", "right" ) ;
        jQuery( '<span/>', {
            class: "tedit",
            "data-path": "performance." + metricId + ".delta",
            "data-truefalse": true,
            text: isDelta
        } ).appendTo( $deltaDiv ) ;

        return $deltaDiv ;

    }






    function updateServiceDefinition( operation, isUpdate, globalDefinitionUpdateFunction, newName, message ) {


        if ( jsonForms.areThereErrors() ) {
            return ;
        }
        // need sync _serviceJson with editors
        if ( _isJsonEditorActive ) {
            // only submit if parsing is passing
            // if ( !parseAndUpdateJsonEdits( $( _definitionTextAreaId ) ) ) {


            if ( !jsonForms.parseAndUpdateJsonEdits( _serviceJson, $( _definitionSelector ), _definitionSelector ) ) {
                alertify.alert( "Parsing errors must be corrected prior to further processing" ) ;
                return ;
            } else {
                _serviceJson = JSON.parse( $( _definitionSelector ).val() ) ;
            }
        }


        let paramObject = {
            operation: operation,
            newName: newName,
            project: utils.getActiveProject(),
            serviceName: $( "#dialogServiceSelect" ).val(),
            hostName: _hostName,
            definition: JSON.stringify( _serviceJson, null, "\t" ),
            message: "Service Settings: " + message
        } ;
        console.log( `updateServiceDefinition(): operation: ${ operation }` ) ;



        if ( operation == "notify" ) {

            $.extend( paramObject, {
                itemName: paramObject.serviceName
            } )
            $.post( SERVICE_DEF_URL + "/../notify", paramObject )
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

        $.post( SERVICE_DEF_URL, paramObject )

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
                        $userMessage.append( $moreInfo ) ;

                        if ( operation != "modify" ) {
                            // $userMessage.append( "<br/><br/>Remember to kill/clean services before applying changes to cluster" );
                            okFunction = function () {
                                console.log( "Closed results" ) ;

                                if ( isUpdate ) {
                                    jsonForms.closeDialog() ;
                                }
                            }
                        }


                        if ( _refreshLifecycleFunction != null ) {
                            _refreshLifecycleFunction() ;
                        } else {
                            console.log( "Skipping refreshFunction" ) ;
                        }
                    }


                    if ( !isUpdate ) {
                        $userMessage.append( `<div class="csap-info">Uncheck validation only to commit changes</div>` ) ;
                    }

                    if ( updatesResult.message ) {
                        $userMessage.append( `<div class="csap-info">${ updatesResult.message }</div>`  ) ;
                    }


                    if ( globalDefinitionUpdateFunction != null ) {
                        globalDefinitionUpdateFunction( true ) ;
                    }

                    jsonForms.showUpateResponseDialog( resultsTitle, $userMessage.html(), okFunction ) ;


                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    failedToGetService( "updating" + SERVICE_DEF_URL, errorThrown ) ;

                    utils.loadingComplete() ;
                    //alertify.error( "Unable to retreive service", "Url: " + SERVICE_DEF_URL + "<br>" );
                } ) ;
    }
    ;



    function failedToGetService( command, errorThrown ) {

        if ( errorThrown == "abort" ) {
            console.log( "Request was aborted: " + command ) ;
            return ;
        }
        let message = "Failed to get service: " ;
        message += "<br><br>Command: " + command
        message += '<br><br>Server Response:<pre class="error" >' + errorThrown + "</pre>" ;

        let errorDialog = alertify.alert( message ) ;

        errorDialog.setting( {
            title: "Unable to get/update service instance",
            resizable: false,
            'labels': {
                ok: 'Close'
            },
            'onok': function () {
                // document.location.reload( true );
            }

        } ) ;

        $( 'body' ).css( 'cursor', 'default' ) ;
    }


} ) ;
