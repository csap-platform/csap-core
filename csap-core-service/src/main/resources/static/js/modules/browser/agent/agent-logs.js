
import _dom from "../../utils/dom-utils.js";
import _net from "../../utils/net-utils.js";


import utils from "../utils.js"


import fileMonitor from "../file/file-monitor.js"


const agentLogs = agent_logs();

export default agentLogs;


function agent_logs() {

    _dom.logHead( "Module loaded" );


    let init;

    const $logsPanel = utils.findContent( "#agent-tab-logs" );

    return {

        show: function ( $menuContent, forceHostRefresh, menuPath ) {

            _dom.logArrow( `showing logs for  '${ utils.getSelectedService() }'`) ;

            if ( !init ) {
                registerForEvents();
                return logMonitorTemplate( forceHostRefresh );
            }


            let activePanelId = fileMonitor.getActivePanelId();

            console.log( `activePanelId: ${ activePanelId } ` );

            if ( !forceHostRefresh
                && $logsPanel.attr( "id" ) === activePanelId ) {
                return null;
            }

            return logMonitorTemplate( forceHostRefresh );

        }

    };

    function registerForEvents() {
        init = true;
        utils.getServiceSelector().change( function () {

            logMonitorTemplate( true );

        } );
    }



    function logMonitorTemplate( forceHostRefresh ) {



        let selectedService = utils.getSelectedService();
        if ( selectedService === utils.agentServiceSelect() ) {
            selectedService = "csap-agent";
        }

        let listingUrl = `${ FILE_URL }/FileMonitor`;



        let parameters = {
            serviceName: selectedService,
            hostName: utils.getHostName(),
            agentUi: true,
        };

        if ( utils.getPageParameters().has( "fileName" ) ) {
            parameters.fileName = utils.getPageParameters().get( "fileName" );
        }

        if ( utils.getPageParameters().has( "journal" ) ) {
            parameters = {
                fileName: "__journal__/" + utils.getPageParameters().get( "journal" )
            };
            utils.getPageParameters().delete( "journal" );
        }

        utils.loading( `loading logs: ${ utils.getHostName() } `, parameters );


        const listHandler = function ( lifeDialogHtml ) {

            let $logListing = $( "<html/>" ).html( lifeDialogHtml ).find( '.csap-file-monitor' );

            if ( lifeDialogHtml.startsWith( "error" ) ) {
                $logListing = jQuery( '<div/>', {
                    class: "warning",
                    text: lifeDialogHtml
                } );

                $logsPanel.html( $logListing );
            } else {

                $logsPanel.html( $logListing );

                let $logFileSelect = $( "#logFileSelect", $logsPanel );

                fileMonitor.show( $logsPanel, utils.getHostName(), selectedService, $logFileSelect );
            }
            utils.loadingComplete();

        }

        let viewLoadedPromise = _net.httpGet( listingUrl, parameters, true );

        viewLoadedPromise
            .then( listHandler )
            .catch( ( e ) => {
                console.warn( e );
            } );;


        return viewLoadedPromise;


    }



}

