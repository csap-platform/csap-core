
// // http://requirejs.org/docs/api.html#packages
// // Packages are not quite as ez as they appear, review the above
// require.config( {

// } );
// require( [], function ( ) {
//     console.log( "\n\n ************** module loaded *** \n\n" );

console.log( `loading imports` );


import "../libs/csap-modules.js";
import { _dialogs, _dom, _utils, _net } from "../utils/all-utils.js";


// import _dialogs from "./utils/dialog-utils.js";
// import _utils from "./utils/app-utils.js";
// import _dom from "./utils/dom-utils.js";
// import _net from "./utils/net-utils.js";
//import _legacyUtils from "./utils/legacy-utils.js";



_dom.onReady( function () {

    _utils.prefixColumnEntryWithNumbers( $( "table" ), false );

    let appScope = new api_navigator( globalThis.settings );

    // _dialogs.loading( "start up" );

    appScope.initialize();

} );



function api_navigator() {


    let $templateFilter;
    let _refreshTimer;


    this.initialize = function () {


        $( "#csapPageLabel" ).hide();

        $( "#csapPageVersion" ).css( "margin-left", "3em" );

        $( "table a.simple" ).click( function ( e ) {
            e.preventDefault();
            let url = $( this ).attr( 'href' );
            window.open( url, '_blank' );
        } );

        $( "img.csapDocImage" )
            .attr( "title", "Click to toggle display size" )
            .click(
                function ( e ) {
                    $( this ).toggleClass(
                        'csapDocImageEnlarged' );
                } );

        $( "button.smallSubmit" ).click(
            function () {
                alertify
                    .error(
                        "Submitting request. Page will auto-refresh on complete",
                        0 );
                $( this ).parent().submit();
            } );

        $templateFilter = $( "#api-filter" );

        $templateFilter.off().keyup( function () {
            console.log( "Applying template filter" );
            clearTimeout( _refreshTimer );
            _refreshTimer = setTimeout( function () {
                applyFilter();
            }, 500 );
        } );
    }

    jQuery.expr[ ':' ].icontains = function ( element, index, match ) {

        // console.log(`element`, element, `index: ${ index}, match `, match) ;
        return jQuery( element ).text().toUpperCase()
            .indexOf( match[ 3 ].toUpperCase() ) >= 0;

    };

    function applyFilter() {

        let $body = $( "table#api tbody" );
        let $rows = $( 'tr', $body );

        let filter = $templateFilter.val();

        console.log( "filter", filter );

        if ( filter.length > 0 ) {
            $rows.hide();
            $( 'td div.api-path:icontains("' + filter + '")', $rows ).parent().parent().show();
        } else {
            $rows.show();
        }
    }



}