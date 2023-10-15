
console.log( `loading imports` );

import "../libs/csap-modules.js";
import "../utils/table-test-page.js" ;

import { _dialogs, _dom, _utils, _net } from "../utils/all-utils.js";


import subModule from "./sub-1.js";



_dom.onReady( function () {


    let appScope = new sample_demo( globalThis.settings );

    _dialogs.loading( "start up" );

    appScope.initialize();

} );

function sample_demo( mySettings ) {


    const $loadingPanel = $( "#loading-project-message" );

    
    const tableThemes = ["metro-dark", "blue" ] ; // Other themes need to be added to landing page
    let currentTheme = 0 ;
    let needsInit = true ;


    this.initialize = function () {

        _dom.logSection( `initializing main ` );

        //$( 'header' ).text( 'Hi from jQuery!' );

        //_dialogs.loading


        $( '#currentTimeButton' ).click( getTime );
        $( '#time-cors-button' ).click( getTimeCors );

        
        setTimeout(() => { 
            _dialogs.loadingComplete() ;
        }, 500); 

        $( '#themeTablesButton' ).click( function () {

            let $table = $( 'table' ) ;
            console.log( "currentTheme:", tableThemes[currentTheme] ) ;
    
            if ( needsInit ) {
                needsInit = false ;
                $table.tablesorter( {
                    widgets: ['uitheme', 'filter'],
                    theme: tableThemes[ currentTheme ]
                } ) ;
    
    
                $( "table" ).removeClass( "simple" ).css( "width", "80em" )
                        .css( "margin", "2em" ) ;
    
            } else {
    
                $table[0].config.theme = tableThemes[ currentTheme ] ;
                $table.trigger( 'applyWidgets' ) ;
            }
    
    
            if ( ++currentTheme >= tableThemes.length )
                currentTheme = 0 ;
    
    
        } ) ;

    }

    function getTime() {

        $( 'body' ).css( 'cursor', 'wait' );

        $.get( "currentTime",
            function ( data ) {
                // alertify.alert(data) ;
                alertify.dismissAll();
                // alertify.csapWarning( '<pre style="font-size: 0.8em">' + data
                //     + "</pre>" )
                    _dialogs.csapWarning( '<pre style="font-size: 0.8em">' + data
                        + "</pre>" )

                    setTimeout(() => { 
                        $( 'body' ).css( 'cursor', 'default' )
                    }, 500); 
               ;
            }, 'text' // I expect a JSON response
        );

    }

    function getTimeCors() {


        $( 'body' ).css( 'cursor', 'wait' );

        $.get( "http://peter.yourcompany.org:8080/cors/currentTime",
            function ( data ) {
                // alertify.alert(data) ;
                alertify.dismissAll();
                alertify.csapWarning( '<pre style="font-size: 0.8em">' + data
                    + "</pre>" )
                $( 'body' ).css( 'cursor', 'default' );
            }, 'text' // I expect a JSON response
        );

    }


}