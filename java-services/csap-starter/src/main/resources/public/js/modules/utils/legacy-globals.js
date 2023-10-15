/**
 * 
 * Jquery based helpers functions
 * 
 */

 import _dom from "../utils/dom-utils.js";


 _dom.logArrow( "JQuery ajaxSetup: disabled cache, traditional params" ) 

$.ajaxSetup( {
    cache: false,
    traditional: true
    // traditional is critical for form based param processing.
    // timeout : deployTimeout, never timeout
} );


jQuery.each( [ "put", "delete" ], function ( i, method ) {
    jQuery[ method ] = function ( url, data, callback, type ) {
        if ( jQuery.isFunction( data ) ) {
            type = type || callback;
            callback = data;
            data = undefined;
        }

        return jQuery.ajax( {
            url: url,
            type: method,
            dataType: type,
            data: data,
            success: callback
        } );
    };
} );


window.CSAP_THEME_COLORS = [ "#5DA5DA", "#FAA43A", "#60BD68", "#F17CB0", "#B2912F", "#B276B2", "#DECF3F", "#F15854", "#4D4D4D" ];

String.prototype.contains = function ( searchString ) {
    //return this.indexOf( searchString ) !== -1;
    return this.includes( searchString );
};

window.jsonToString = function jsonToString( jsonObject ) {
    return JSON.stringify( jsonObject, null, "\t" )
}

window.numberWithCommas = function ( x ) {
    return x.toString().replace( /\B(?=(\d{3})+(?!\d))/g, "," );
}


// eg. numberOfSamples becomes number Of Samples
window.splitUpperCase = function ( key ) {

    var resultWithSpaces = ""; // lowercase first word
    for ( var i = 0; i < key.length; i++ ) {
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
}

window.handleConnectionError = function ( command, errorThrown ) {

    if ( errorThrown == "abort" ) {
        console.log( "Request was aborted: " + command );
        return;
    }
    var message = "Failed to send request to server. Try again in a few minutes.";
    message += "<br><br>Command: " + command
    message += '<br><br>Server Response:<pre class="error" >' + errorThrown + "</pre>";

    var errorDialog = alertify.confirm( message );

    errorDialog.setting( {
        title: "Host Connection Error",
        resizable: false,
        'labels': {
            ok: 'Reload Page',
            cancel: 'Ignore Error'
        },
        'onok': function () {
            document.location.reload( true );
        },
        'oncancel': function () {
            alertify.warning( "Wait a few seconds and try again," );
        }

    } );

    $( 'body' ).css( 'cursor', 'default' );
}


// jQuery.expr[':'].icontains = function ( a, i, m ) {
$.expr.pseudos.icontains = function ( a, i, m ) {
    let contents = jQuery( a ).text().toUpperCase() ;

    //console.log(`contents of filter`, contents) ;
    let expression = m[3].toUpperCase() ;

    //console.log(`contents: ${contents}, expression: ${expression} `) ;
    return contents.indexOf( expression ) >= 0 ;
} ;

// var localTime = new Date();
// var remoteTime = new Date();
// remoteTime.setMinutes(remoteTime.getMinutes() + localTime.getTimezoneOffset()
// +930);
// console.log( "localTime:" + localTime + "localTime.getTimezoneOffset(): " +
// localTime.getTimezoneOffset() + " remoteTime:" + remoteTime) ;



// Sorting html selects. Default sorts based on text value of option, but param
// can be passed
//
$.fn.sortSelect = function ( sortByAttribute ) {

    sortByAttribute = typeof sortByAttribute !== 'undefined' ? sortByAttribute : "text";
    // Get options from select box
    var my_options = $( "option", this );

    var selectedText = $( "option:selected", this ).text();
    // console.log("sortSelect selectedText: " + selectedText) ;
    // sort alphabetically
    my_options.sort( function ( a, b ) {

        let aVal = a.text;
        let bVal = b.text;
        if ( sortByAttribute != "text" ) {
            aVal = a.getAttribute( sortByAttribute );
            bVal = b.getAttribute( sortByAttribute );
        }
        if ( aVal.toLowerCase() > bVal.toLowerCase() )
            return 1;
        else if ( aVal.toLowerCase() < bVal.toLowerCase() )
            return -1;
        else
            return 0
    } )
    // replace with sorted my_options, preserving selected text
    $( this ).empty().append( my_options );

    $( "option", this ).each( function ( index ) {
        // console.log("Re-Checking: " + $(this).text() + " against: " +
        // selectedText) ;
        if ( $( this ).text() == selectedText ) {
            // console.log("Re-Selecting: " + selectedText) ;
            $( this ).prop( "selected", "selected" );
        }
    } );
}


window.executionTime = function ( callback ) {
    var start, done;
    start = new Date();
    callback.call();
    done = new Date();
    return done.getTime() - start.getTime();
}

/**
 * Hook to leave console lines in code for debugging. Use sparingly though
 * 
 */
if ( !window.console ) {

    ( function () {
        var names = [ "log", "debug", "info", "warn", "error", "assert", "dir",
            "dirxml", "group", "groupEnd", "time", "timeEnd", "count",
            "trace", "profile", "profileEnd" ], i, l = names.length;

        window.console = {};

        for ( i = 0; i < l; i++ ) {
            window.console[ names[ i ] ] = function () {
            };
        }
    }() );

}

/**
 * Check whether a JQ object has a scrollbar.
 * 
 */
( function ( $ ) {
    $.fn.hasScrollBar = function () {
        return this.get( 0 ).scrollHeight > this.height();
    };
} )( jQuery );

/**
 * get a param: if ( $.urlParam("nav") != null) navUrl=$.urlParam("nav") ;
 */
$.urlParam = function ( name ) {
    var results = new RegExp( '[\\?&]' + name + '=([^&#]*)' )
        .exec( window.location.href );
    if ( results == null ) {
        return null;
    }
    return results[ 1 ] || 0;
};

/**
 * Get the column from the row
 * 
 */

$.fn.column = function ( i ) {
    return $( 'td:nth-child(' + ( i + 1 ) + ')', this );
};

/**
 * Helper for displaying js object type
 * 
 * @param obj
 * @returns
 */
window.type = function ( obj ) {
    return Object.prototype.toString.call( obj ).match( /^\[object (.*)\]$/ )[ 1 ];
}

/**
 * IE chokes on some chars
 */
window.openWindowSafely = function ( url, windowFrameName ) {

    console.log( `url: ${ url },  window frame name: ${ getValidWinName( windowFrameName ) }` );
    // console.log("window frame name: " + getValidWinName( windowFrameName)
    // + "
    // url: " + encodeURI(url)
    // + " encodeURIComponent:" + encodeURIComponent(url)) ;

    window.open( encodeURI( url ), getValidWinName( windowFrameName ) );

}

window.getValidWinName = function ( inputName ) {
    var regex = new RegExp( "-", "g" );
    var validWindowName = inputName.replace( regex, "" );

    regex = new RegExp( " ", "g" );
    validWindowName = validWindowName.replace( regex, "" );

    return validWindowName;
}

// alert("browser is: " + $.browser.chrome ) ;
/**
 * hook for chrome which struggles with posting to multiple targets only chrome
 * will use winIndex*ms to delay launch
 * 
 */
window.postAndRemove = function ( windowFrameName, urlAction, inputMap ) {

    var $form = $( '<form id="temp_form"></form>' );

    $form.attr( "action", urlAction );
    $form.attr( "method", "post" );
    $form.attr( "target", getValidWinName( windowFrameName ) );

    $.each( inputMap, function ( k, v ) {

        if ( v != null ) {
            $form.append( '<input type="hidden" name="' + k + '" value="' + v
                + '"/>' );
        }
    } );

    console.log( "Post to '" + urlAction + "'", inputMap );

    $( "body" ).append( $form );

    $form.submit();
    $form.remove();

}


_dom.logArrow( "Adding Helpers for flot with Date updated functions" ) 




const csapFlot = csap_flot_helpers();

export default csapFlot

function csap_flot_helpers() {

    return {


        getUsCentralTime: function ( localTime ) {
            var usCentral = new Date( localTime );
            usCentral.setMinutes( usCentral.getMinutes() + localTime.getTimezoneOffset() - 360 );
            return usCentral;
        },

    /**
     * Considerations: - all graphs assume us data centers in central time: CST:
     * -360 - java script concept of timezones is very limited - Users browsing by
     * date are likely wanting to use dates from log files
     *  - selectedTime is always 00:00 of the localtime
     */
        calculateUsCentralDays: function ( selectedTime ) {

            var nowDate = new Date( Date.now() );
            var selectedDate = new Date( selectedTime )

            var deltaDays = Math.floor( ( nowDate.getTime() - selectedDate.getTime() ) / 24 / 60 / 60 / 1000 );
            // var days=Math.round((nowDate.getTime() - selectedDate.getTime()
            // )/24/60/60/1000) ;
            // console.log("Days: " + deltaDays + " selectedDate: " + selectedDate +
            // " nowDate: " + nowDate) ;
            return deltaDays;
        }
    }



}




// Adding Methods to the Date Object to handle Daylight savings

Date.prototype.stdTimezoneOffset = function () {
    var jan = new Date( this.getFullYear(), 0, 1 );
    var jul = new Date( this.getFullYear(), 6, 1 );
    return Math.max( jan.getTimezoneOffset(), jul.getTimezoneOffset() );
}

Date.prototype.dst = function () {
    return this.getTimezoneOffset() < this.stdTimezoneOffset();
}

Date.prototype.addMinutes = function ( m ) {
    this.setMinutes( this.getMinutes() + m );
    return this;
}

var dateFormat = function () {
    var token = /d{1,4}|m{1,4}|yy(?:yy)?|([HhMsTt])\1?|[LloSZ]|"[^"]*"|'[^']*'/g,
            timezone = /\b(?:[PMCEA][SDP]T|(?:Pacific|Mountain|Central|Eastern|Atlantic) (?:Standard|Daylight|Prevailing) Time|(?:GMT|UTC)(?:[-+]\d{4})?)\b/g,
            timezoneClip = /[^-+\dA-Z]/g,
            pad = function ( val, len ) {
                val = String( val );
                len = len || 2;
                while (val.length < len)
                    val = "0" + val;
                return val;
            };

    // Regexes and supporting functions are cached through closure
    return function ( date, mask, utc ) {
        var dF = dateFormat;

        // You can't provide utc if you skip other args (use the "UTC:" mask
        // prefix)
        if ( arguments.length == 1 && Object.prototype.toString.call( date ) == "[object String]" && !/\d/.test( date ) ) {
            mask = date;
            date = undefined;
        }

        // Passing date through Date applies Date.parse, if necessary
        date = date ? new Date( date ) : new Date;
        if ( isNaN( date ) )
            throw SyntaxError( "invalid date" );

        mask = String( dF.masks[mask] || mask || dF.masks["default"] );

        // Allow setting the utc argument via the mask
        if ( mask.slice( 0, 4 ) == "UTC:" ) {
            mask = mask.slice( 4 );
            utc = true;
        }

        var _ = utc ? "getUTC" : "get",
                d = date[_ + "Date"](),
                D = date[_ + "Day"](),
                m = date[_ + "Month"](),
                y = date[_ + "FullYear"](),
                H = date[_ + "Hours"](),
                M = date[_ + "Minutes"](),
                s = date[_ + "Seconds"](),
                L = date[_ + "Milliseconds"](),
                o = utc ? 0 : date.getTimezoneOffset(),
                flags = {
                    d: d,
                    dd: pad( d ),
                    ddd: dF.i18n.dayNames[D],
                    dddd: dF.i18n.dayNames[D + 7],
                    m: m + 1,
                    mm: pad( m + 1 ),
                    mmm: dF.i18n.monthNames[m],
                    mmmm: dF.i18n.monthNames[m + 12],
                    yy: String( y ).slice( 2 ),
                    yyyy: y,
                    h: H % 12 || 12,
                    hh: pad( H % 12 || 12 ),
                    H: H,
                    HH: pad( H ),
                    M: M,
                    MM: pad( M ),
                    s: s,
                    ss: pad( s ),
                    l: pad( L, 3 ),
                    L: pad( L > 99 ? Math.round( L / 10 ) : L ),
                    t: H < 12 ? "a" : "p",
                    tt: H < 12 ? "am" : "pm",
                    T: H < 12 ? "A" : "P",
                    TT: H < 12 ? "AM" : "PM",
                    Z: utc ? "UTC" : ( String( date ).match( timezone ) || [""] ).pop().replace( timezoneClip, "" ),
                    o: ( o > 0 ? "-" : "+" ) + pad( Math.floor( Math.abs( o ) / 60 ) * 100 + Math.abs( o ) % 60, 4 ),
                    S: ["th", "st", "nd", "rd"][d % 10 > 3 ? 0 : ( d % 100 - d % 10 != 10 ) * d % 10]
                };

        return mask.replace( token, function ( $0 ) {
            return $0 in flags ? flags[$0] : $0.slice( 1, $0.length - 1 );
        } );
    };
}();

// Some common format strings
dateFormat.masks = {
    "default": "ddd mmm dd yyyy HH:MM:ss",
    shortDate: "m/d/yy",
    mediumDate: "mmm d, yyyy",
    longDate: "mmmm d, yyyy",
    fullDate: "dddd, mmmm d, yyyy",
    shortTime: "h:MM TT",
    mediumTime: "h:MM:ss TT",
    longTime: "h:MM:ss TT Z",
    isoDate: "yyyy-mm-dd",
    isoTime: "HH:MM:ss",
    isoDateTime: "yyyy-mm-dd'T'HH:MM:ss",
    isoUtcDateTime: "UTC:yyyy-mm-dd'T'HH:MM:ss'Z'"
};

// Internationalization strings
dateFormat.i18n = {
    dayNames: [
        "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat",
        "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
    ],
    monthNames: [
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
        "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"
    ]
};

// For convenience...
Date.prototype.format = function ( mask, utc ) {
    return dateFormat( this, mask, utc );
};

/**
 * Considerations: - all graphs assume us data centers in central time: CST:
 * -360 - java script concept of timezones is very limited - Users browsing by
 * date are likely wanting to use dates from log files
 *  - selectedTime is always 00:00 of the localtime
 */
function calculateUsCentralDays( selectedTime ) {

    var nowDate = new Date( Date.now() );
    var selectedDate = new Date( selectedTime )

    var deltaDays = Math.floor( ( nowDate.getTime() - selectedDate.getTime() ) / 24 / 60 / 60 / 1000 );
    // var days=Math.round((nowDate.getTime() - selectedDate.getTime()
    // )/24/60/60/1000) ;
    // console.log("Days: " + deltaDays + " selectedDate: " + selectedDate +
    // " nowDate: " + nowDate) ;
    return deltaDays;
}
// function calculateUsCentralDays( selectedTime ) {
// var nowDate=new Date(Date.now());
// var selectedUsOffset = 5 * 60 * 60 * 1000 ; // 5 hours in milliseconds
// var selectedDate = new Date ( selectedTime + selectedUsOffset ) ;
//
// var nowUtc = new Date(nowDate.getUTCFullYear(), nowDate.getUTCMonth(),
// nowDate.getUTCDate(), nowDate.getUTCHours() , nowDate.getUTCMinutes(),
// nowDate.getUTCSeconds());
//	
// nowUtc.setTime( nowUtc.getTime() - selectedUsOffset ) ;
// console.log( "nowDate.getUTCHours(): " + nowDate.getUTCHours() + " nowUtc: "
// + nowUtc + " nowDate: " + nowDate) ;
//
// // console.log("Timezone offset is: " + selectedDate.getTimezoneOffset() ) ;
// // console.log("nowCentralOffset: " + (nowCentralOffset/60000) + "\n
// selectedDate: " + selectedDate
// // + "\n nowDate: " + nowDate + " nowUtc: " + nowUtc ) ;
// //
// var days=Math.round(
// (nowUtc.getTime() - selectedDate.getTime() )/24/60/60/1000) ;
// return days ;
// }

function getUsCentralTime( localTime ) {
    var usCentral = new Date( localTime );
    usCentral.setMinutes( usCentral.getMinutes() + localTime.getTimezoneOffset() - 360 );
    return usCentral;
}