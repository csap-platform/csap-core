
import _dom from "../../utils/dom-utils.js";

import utils from "../utils.js"

import events from "./events.js"

const eventDetails = event_details();

export default eventDetails ;


function event_details() {
    let lastPodName ;

    let _editors = new Object() ;

    return {

        show: function ( $container, forceHostRefresh, menuPath ) {

            console.log( `details for ${ $container.attr( "id" ) }` ) ;

            initialize( $container ) ;

            return loadAttributeDetails( forceHostRefresh, $container ) ;

        }

    } ;

    function initialize( $container ) {

        let containerId = $container.attr( "id" ) ;

        if ( !_editors [ containerId ] ) {

            let editorId = containerId + "-editor" ;
            let $editorContainer = $( ".describe-editor", $container ) ;
            $editorContainer.attr( "id", editorId ) ;

            console.log( ` Building: describe editor: ${ editorId } ` ) ;
            _editors [ containerId ] = aceEditor.edit( editorId ) ;

            _editors [ containerId ].setOptions( utils.getAceDefaults( "ace/mode/yaml", true ) ) ;

            $( "#close-details", $container ).click( function () {
                events.closeDetails() ;
            } ) ;


            $( ".view-definition", $container ).change( function () {
                loadAttributeDetails( true, $container ) ;
            } ) ;

            $( ".code-fold", $container ).change( function () {
                if ( $( this ).is( ':checked' ) ) {
                    console.log( "Initiating fold" ) ;
                    setTimeout( function () {
                        _editors [ containerId ].getSession().foldAll( 2 ) ;
                    }, 100 )

                } else {
                    //_yamlEditor.getSession().unfoldAll( 2 ) ;
                    _editors [ containerId ].getSession().unfold( ) ;
                }
            } ) ;
        }

    }

    function loadAttributeDetails( forceHostRefresh, $container ) {

        let selectedEvent = events.selected() ;

        if ( !selectedEvent ) {
            events.closeDetails() ;
            return ;
        }

        let $contentLoaded = new $.Deferred() ;
        utils.loading( "Loading Event" ) ;

        let parameters = {
            id: selectedEvent.id
        } ;
        $.getJSON(
                APP_BROWSER_URL + "/event",
                parameters )

                .done( function ( eventReport ) {

                    console.log( `getEvents() : showEvent` ) ;
                    utils.loadingComplete()
                    //alertify.csapInfo( JSON.stringify( eventReport, "\n", "\t" ) ) ;
                    let csapText = utils.json( "data.csapText", eventReport ) ;
                    let eventText = "" ;
                    if ( csapText ) {
                        eventText += "\n\n --------------------- Event Text ---------------------\n"
                        eventText += csapText ;
                        eventText += "\n\n --------------------- Event Raw ---------------------\n"
                    }
                    eventText += JSON.stringify( eventReport, "\n", "\t" ) ;
                    updateEditor( eventText, $container )

                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    handleConnectionError( `retrieving alerts '${textStatus}'`, errorThrown ) ;
                } ) ;


        return $contentLoaded ;


    }

    function updateEditor( eventDetails, $container ) {

        _editors [ $container.attr( "id" ) ].getSession().setValue( eventDetails ) ;

        $( ".code-fold", $container ).trigger( "change" ) ;


    }


}

