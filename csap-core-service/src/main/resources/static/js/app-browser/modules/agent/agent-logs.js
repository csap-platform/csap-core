define( [ "browser/utils", "file/file-monitor" ], function ( utils, fileMonitor ) {

    console.log( "Module loaded5" ) ;
    
    let init ;

    const $logsPanel = utils.findContent( "#agent-tab-logs" ) ;

    return {

        show: function ( $menuContent, forceHostRefresh, menuPath ) {

            if ( !init ) {
                registerForEvents() ;
                return logMonitorTemplate( forceHostRefresh ) ;
            }
            
            
            let activePanelId = fileMonitor.getActivePanelId() ;
            
            console.log(  `activePanelId: ${ activePanelId } `) ;
            
            if ( !forceHostRefresh 
                    && $logsPanel.attr("id") === activePanelId) {
                return null ;
            }
            
             return logMonitorTemplate( forceHostRefresh ) ;

        }

    } ;

    function registerForEvents() {
        init = true ;
        utils.getServiceSelector().change( function () {

            logMonitorTemplate( true ) ;

        } ) ;
    }



    function logMonitorTemplate( forceHostRefresh ) {
       


        let selectedService = utils.getSelectedService() ;
        if ( selectedService === utils.agentServiceSelect() ) {
            selectedService = "csap-agent" ;
        }
        
        let listingUrl = `${ FILE_URL }/FileMonitor` ;



        let parameters = {
            serviceName: selectedService,
            hostName: utils.getHostName(),
            agentUi: true,
        } ;

        if ( utils.getPageParameters().has( "fileName" ) ) {
            parameters.fileName = utils.getPageParameters().get( "fileName" ) ;
        }

        if ( utils.getPageParameters().has( "journal" ) ) {
            parameters = {
                fileName: "__journal__/" + utils.getPageParameters().get( "journal" )
            } ;
            utils.getPageParameters().delete( "journal" ) ;
        }

//        if ( selectedService === utils.getDefaultService() ) {
//            if ( utils.getPageParameters( ).has("container") ) {
//                parameters.containerName = utils.getPageParameters( ).get("container") ;
//            }
//        }

//        if ( ! instances.isKubernetes() &&
//                (! utils.isUnregistered( selectedService )) ) {
//            delete parameters.containerName ;
//        }

//        console.log( `loading: ${listingUrl}`, parameters, `\n all hosts: `, allHosts ) ;

        utils.loading( `loading logs: ${ utils.getHostName() } `, parameters ) ;

        let $contentLoaded = new $.Deferred() ;

        $.get( listingUrl,
                parameters,
                function ( lifeDialogHtml ) {
                    // console.log( ` content: `, lifeDialogHtml ) ;


                    let $logListing = $( "<html/>" ).html( lifeDialogHtml ).find( '.csap-file-monitor' ) ;

                    if ( lifeDialogHtml.startsWith( "error" ) ) {
                        $logListing = jQuery( '<div/>', {
                            class: "warning",
                            text: lifeDialogHtml
                        } ) ;

                        $logsPanel.html( $logListing ) ;
                    } else {

                        $logsPanel.html( $logListing ) ;
                        
                         let $logFileSelect = $( "#logFileSelect", $logsPanel ) ;

                        fileMonitor.show( $logsPanel, utils.getHostName(), selectedService, $logFileSelect ) ;
                    }
                    utils.loadingComplete() ;

                    $contentLoaded.resolve() ;
                },
                'text' ) ;


        return $contentLoaded ;


    }



} ) ;

