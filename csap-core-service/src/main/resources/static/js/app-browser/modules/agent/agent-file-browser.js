define( [ "browser/utils", "file/FileManager", "ace/ace", "ace/ext-modelist" ], function ( utils, FileManager, aceEditor, aceModeList ) {

    console.log( "Module loaded" ) ;

    let fileManager ;
    

    const $projectPanel = utils.findContent( "#agent-tab-file-browser" ) ;
    
    let init ;

    return {

        show: function ( $menuContent, forceHostRefresh, menuPath ) {
            
            if ( ! init ) {
                registerForEvents() ;
            }


            return getFileBrowser( $menuContent, forceHostRefresh, menuPath ) ;

        }

    } ;
    
    
    function registerForEvents() {
        init = true ;
        utils.getServiceSelector().change( function () {
            $.when( getFileBrowser( null, true, null ) ).done( function() {
                utils.loadingComplete() ;
            }) ;
        } ) ;
    }



    function getFileBrowser( $menuContent, forceHostRefresh, menuPath ) {



        let locationLabel = "Application Definition" ;
        let location = "__platform__\/definition" ;
        //location ="__root__" ;

        if ( utils.adminHost() == "localhost" ) {
            location = "__root__/aaa" ;
            location ="__home__/git/***REMOVED***-desktop-definition" ;
        }
        
        let showShortCuts = true ;
        if ( utils.getPageParameters().has( "fromFolder" ) ) {
            locationLabel = utils.getPageParameters().get( "fromFolder" ) ;
            location = `__root__/${ locationLabel }` ;
            showShortCuts=false;
        }
        
        if ( utils.getPageParameters().has("quickview") ) {
            locationLabel = utils.getPageParameters().get("quickview") ;
        }
        
        let serviceName = null ;
        
        let selectedService = utils.getServiceSelector().val() ;
        if ( utils.getPageParameters().has( "serviceAndClear" ) ) {
            selectedService = utils.getPageParameters().get( "serviceAndClear" ) ;
            utils.getPageParameters().delete( "serviceAndClear" ) ;
        }
        console.log( `selectedService: ${selectedService}`) ;
        if ( selectedService === null ) {
            console.log( `files only service - using text value of left nav` );
           selectedService = $( "span", utils.getServiceSelector().parent() ).text() ;
        }
        if ( selectedService !== utils.agentServiceSelect() ) {
            
            serviceName = selectedService ;
            location="." ;
            locationLabel= null;
            showShortCuts=false;
        }

        
        if ( utils.getPageParameters().has( "locationAndClear" ) ) {
            location = `${ utils.getPageParameters().get( "locationAndClear" ) }` ;
            locationLabel = utils.getPageParameters().get( "locationAndClear" ) ;
            if ( locationLabel.startsWith("__root__") ) {
                locationLabel = locationLabel.substr(8) ;
            } else if ( locationLabel.startsWith("__docker__") ) {
                locationLabel = "Container: " + locationLabel.substr(10) ;
            }
             utils.getPageParameters().delete( "locationAndClear" ) ;
            showShortCuts=false;
            forceHostRefresh=true;
        }
        
        
        
        if ( !forceHostRefresh && ( fileManager != null ) ) {
            return ;
        }



        let $contentLoaded = new $.Deferred() ;

        let parameters = {
            serviceName: serviceName, 
            quickview: locationLabel,
            fromFolder: location
        } ;
        
        if ( utils.getPageParameters().has("nfs") ) {
            parameters.nfs = utils.getPageParameters().get("nfs") ;
            parameters.fromFolder = utils.getPageParameters().get("fromFolder") ;
        }


        $projectPanel.hide() ;
        utils.loading( `Loading summary for ${  utils.getActiveProject( false ) }` ) ;
        //targetUrl + "&callback=?",  
        let summaryUrl = `${ FILE_URL }/FileManager` ;
        console.log( `loading: ${summaryUrl}` ) ;


        $.get( summaryUrl,
                parameters,
                function ( lifeDialogHtml ) {
                    let $summaryContent = $( "<html/>" ).html( lifeDialogHtml ).find( '.csap-file-browser' ) ;

                    console.log( ` content: `, $summaryContent[0] ) ;
                    $projectPanel.html( $summaryContent ) ;
                    
                    if ( utils.getPageParameters().has("nfs") ) {
                        // update location using server resolved path
                        location = $( "aside input.fromDisk ", $projectPanel ).val()  ;
                        console.log( `Resolved nfs location: ${ location } ` ) ;
                    }

                    //utils.loadingComplete() ;
                    $projectPanel.show() ;

                    //
                    fileManager = new FileManager(
                            utils.adminHost(),
                            $projectPanel,
                            utils.getCsapUser(),
                            serviceName,
                            locationLabel,
                            location,
                            true ) ;

                    fileManager.info() ;


                    fileManager.initialize( showShortCuts ) ;
                    

                    //fileManager = new FileManager( $fileTree ) ;

                    $contentLoaded.resolve() ;
                },
                'text' ) ;


        return $contentLoaded ;


    }


} ) ;

