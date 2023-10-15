// import "../../../webjars/jquery/3.6.0/jquery.min.js";

import _dom from "./dom-utils.js";


_dom.logHead( "Module loaded" );


export default function _csapCommon() {
}

_csapCommon.calculateAverage = function ( numbers ) {

    const total = numbers.reduce( ( acc, c ) => acc + c, 0 );
    return total / numbers.length;
}

_csapCommon.calculateStandardDeviation = function ( array ) {
    const n = array.length
    const mean = array.reduce( ( a, b ) => a + b ) / n
    return Math.sqrt( array.map( x => Math.pow( x - mean, 2 ) ).reduce( ( a, b ) => a + b ) / n )
}

_csapCommon.findMedian = function ( numbers ) {

    if ( ! Array.isArray( numbers) ) {
        return numbers ;
    }

    const sorted = numbers.slice().sort( ( a, b ) => a - b );
    const middle = Math.floor( sorted.length / 2 );

    if ( sorted.length % 2 === 0 ) {
        return ( sorted[ middle - 1 ] + sorted[ middle ] ) / 2;
    }

    return sorted[ middle ];
}

_csapCommon.enableCorsCredentials = function () {

    // traditional is critical for servlet based param processing.
    // timeout : deployTimeout, never timeout

    console.log( "JQuery ajaxSetup: disabled cache, enabled CORS via xhrFields, traditional params" );
    $.ajaxSetup( {
        cache: false,
        traditional: true,
        headers: {
            'Authorization': bearerAuth
        },
        xhrFields: {
            withCredentials: true // CORS Supports @CrossOrigin
        }
    } );
};

_csapCommon.splitUpperCase = function ( key ) {

    let resultWithSpaces = ""; // lowercase first word
    for ( let i = 0; i < key.length; i++ ) {
        if ( key.charAt( i ) == "_" )
            continue; // strip underscore
        // if ( i > 0 && key.charAt(i-1) == key.charAt(i-1).toUpperCase() ) {
        // // do not space consecutiive upper let
        // }


        if ( i > 0 && ( key.charAt( i ).toUpperCase() == key.charAt( i ) ) ) {

            // hack for OS
            if ( key.charAt( i ) != "S" && key.charAt( i - 1 ) != "0"
                && ( !( key.charAt( i ) >= 0 && key.charAt( i ) <= 9 ) ) )
                resultWithSpaces += " ";
        }
        resultWithSpaces += key.charAt( i );

    }
    return resultWithSpaces;
};


// jqplot requires consistent length
_csapCommon.padMissingPointsInArray = function ( arrayContainer ) {

    let longestArray = null;
    for ( let i = 0; i < arrayContainer.length; i++ ) {

        let curArray = arrayContainer[ i ];
        if ( longestArray == null || longestArray.length < curArray.length )
            longestArray = curArray;
    }

    for ( let i = 0; i < arrayContainer.length; i++ ) {

        let curArray = arrayContainer[ i ];
        if ( longestArray.length == curArray.length )
            continue;

        // make them the same size.
        for ( let j = 0; j < longestArray.length; j++ ) {
            let longestPoint = longestArray[ j ];
            let curPoint = curArray[ j ];
            // console.log("padd handler") ;
            if ( curPoint == undefined || longestPoint[ 0 ] != curPoint[ 0 ] ) {

                let padPoint = [ longestPoint[ 0 ], 0 ];

                curArray.splice( j, 0, padPoint );
            }
        }

    }

};



_csapCommon.handleConnectionError = function ( command, errorThrown ) {  // function
    // handleConnectionError(
    // command,
    // errorThrown
    // ) {

    if ( errorThrown == "abort" ) {
        console.log( "Request was aborted: " + command );
        return;
    }
    let message = "Failed to send request to server. Try again in a few minutes.";
    message += "<br><br>Command: " + command
    message += '<br><br>Server Response:<pre class="error" >' + errorThrown + "</pre>";

    let errorDialog = alertify.confirm( message );

    errorDialog.setting( {
        title: "Host Connection Error: Reload Page?",
        resizable: false,

        'onok': function () {
            document.location.reload( true );
        },
        'oncancel': function () {
            alertify.warning( "Wait a few seconds and try again," );
        }

    } );

    $( 'body' ).css( 'cursor', 'default' );
};


/**
 * prefix the table columns with an integer
 * @param {*} $table 
 */
_csapCommon.prefixColumnEntryWithNumbers = function ( $table ) {
    $( "tr td:first-child", $table ).each( function ( index ) {

        let $cellPanel = jQuery( '<div/>', {
            class: 'numbered-container',
        } );



        let $label = jQuery( '<div/>', {
            text: ( index + 1 ) + "."
        } ).appendTo( $cellPanel);

        
        let $content = jQuery( '<div/>', { } ).appendTo( $cellPanel);
        $( this ).contents().appendTo( $content ) ;

        //content.append(...oldParent.childNodes);
        $( this ).append( $cellPanel ) ;
        $( "a.csap-link", $cellPanel).css("white-space", "normal") ;
        //$( this ).css("width", "8em") ;
        
    } );
}

_csapCommon.configureCsapToolsMenu = function () {

    let $toolsMenu = $( "header .csapOptions select" );

    $toolsMenu.change( function () {
        let item = $( "header .csapOptions select" ).val();

        if ( item != "default" ) {
            console.log( "launching: " + item );
            if ( item.indexOf( "logout" ) == -1 ) {
                _csapCommon.openWindowSafely( item, "_blank" );
            } else {
                document.location.href = item;
            }
            $( "header .csapOptions select" ).val( "default" )
        }

        $toolsMenu.val( "default" );
        $toolsMenu.selectmenu( "refresh" );

    } );

}

_csapCommon.openWindowSafely = function ( url, windowFrameName ) {

    // console.log("window frame name: " + getValidWinName( windowFrameName)
    // + "
    // url: " + encodeURI(url)
    // + " encodeURIComponent:" + encodeURIComponent(url)) ;

    window.open( encodeURI( url ), _csapCommon.getValidWinName( windowFrameName ) );

}

_csapCommon.getValidWinName = function ( inputName ) {
    let regex = new RegExp( "-", "g" );
    let validWindowName = inputName.replace( regex, "" );

    regex = new RegExp( " ", "g" );
    validWindowName = validWindowName.replace( regex, "" );

    return validWindowName;
}
