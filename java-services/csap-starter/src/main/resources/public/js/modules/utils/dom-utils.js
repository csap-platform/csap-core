
export default function DomUtils() {
}





//
// JS 6 helpers
//

/**
 * register a callback for either DOMContentLoaded, or if already ready
 * 
 * @param {*} callbackFunc 
 */
DomUtils.onReady = function ( callbackFunc ) {
    if ( document.readyState !== 'loading' ) {
        // Document is already ready, call the callback directly
        callbackFunc();
    } else if ( document.addEventListener ) {
        // All modern browsers to register DOMContentLoaded
        document.addEventListener( 'DOMContentLoaded', callbackFunc );
    } else {
        // Old IE browsers
        document.attachEvent( 'onreadystatechange', function () {
            if ( document.readyState === 'complete' ) {
                callbackFunc();
            }
        } );
    }
}




DomUtils.loadScript = function ( scriptUrl, isBlocking = false ) {

    this.logArrow( `loading: ${ scriptUrl } ` ) ;

    let scriptElement = document.createElement("script");
    scriptElement.type = "text/javascript";
    scriptElement.src = scriptUrl ;
    scriptElement.async = ! isBlocking;
    // this.findByCss("head").appendChild(scriptElement);
    document.head.append(scriptElement);
    
};



// <link th:href="@{/webjars/fancytree/2.30.0/dist/skin-win7/ui.fancytree.min.css}"
// rel="stylesheet"
// type="text/css" />
DomUtils.loadCss = function ( cssUrl, isBlocking = false ) {

    this.logArrow( `loading: ${ cssUrl } ` ) ;

    let cssLinkElement = document.createElement("link");
    cssLinkElement.type = "text/css";
    cssLinkElement.href = cssUrl ;
    cssLinkElement.rel="stylesheet" ;
    // this.findByCss("head").appendChild(scriptElement);
    document.head.append(cssLinkElement);
    
};



const lineSeparator = "==================================\n"
DomUtils.logHead = function ( msg ) {
    if ( !window.console || !console.log ) {
        return;
    }
    // return Function.prototype.bind.call(console.log, "prefix");
    // return console.log.bind( window.console, `${ lineSeparator }== %s \n${ lineSeparator }` );
    return console.log.bind( window.console, `--------> %s` );
}();

DomUtils.logArrow = function ( msg ) {
    if ( !window.console || !console.log ) {
        return;
    }
    // return Function.prototype.bind.call(console.log, "prefix");
    // return console.log.bind( window.console, `${ lineSeparator }== %s \n${ lineSeparator }` );
    return console.log.bind( window.console, `---> %s` );
}();

DomUtils.logInfo = function ( msg ) {
    if ( !window.console || !console.log ) {
        return;
    }
    return console.info.bind( window.console );
}();

DomUtils.logError = function ( msg ) {
    if ( !window.console || !console.log ) {
        return;
    }
    return console.error.bind( window.console, `\n*\n**\n*** %s \n**\n*` );
}();

DomUtils.logWarn = function ( msg ) {
    if ( !window.console || !console.log ) {
        return;
    }
    // return Function.prototype.bind.call(console.log, "prefix");
    return console.warn.bind( window.console, `\n*\n**\n*** %s \n**\n*` );
}();

DomUtils.logSection = function ( msg ) {
    if ( !window.console || !console.log ) {
        return;
    }
    // return Function.prototype.bind.call(console.log, "prefix");
    return console.log.bind( window.console, `\n*\n**\n*** %s \n**\n*` );
}();



/**
 * Find First matching dom element with id
 * @param {*} domId 
 * @returns document.element
 */
DomUtils.findById = function ( domId ) {

    return document.getElementById( domId );

}

/**
 * Finds first matching dom element
 * @param {*} cssSelector 
 * @returns {HTMLElement}
 */
DomUtils.findByCss = function ( cssSelector ) {

    return document.querySelector( cssSelector );

}
DomUtils.findAllByCss = function ( cssSelector ) {

    return document.querySelectorAll( cssSelector );

}

DomUtils.findByClass = function ( className ) {

    return document.querySelector( `.${ className }` );

}

DomUtils.findAllByClass = function ( className ) {

    return document.querySelector( `.${ className }` );

}


DomUtils.onClick = function ( domElement, theFunction ) {

    domElement.addEventListener( 'click', theFunction );

}

DomUtils.onChange = function ( domElement, theFunction ) {

    domElement.addEventListener( 'change', theFunction );

}
