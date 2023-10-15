

console.log(`loading imports`) ;

import { _dialogs, _dom } from "../utils/all-utils.js";

// import _dialogs from "./utils/dialog-utils.js";
// import _utils from "./utils/app-utils.js";
// import _dom from "./utils/dom-utils.js";
// import _net from "./utils/net-utils.js";


let appScope;

class Sub_Interface {

    /**
     * demo for mod docs for tests
     */
    static showValues() {

        appScope.showValues();

    }


}
export default Sub_Interface;


_dom.onReady( function () {

    appScope = new sub_demo_module( globalThis.settings );
    appScope.initialize();

} );


function sub_demo_module() {

    const _demo_csap_info_button = _dom.findById( 'demo-csap-info' );
    const _demo_csap_warning_button = _dom.findById( 'demo-csap-warning' );


    let counterVariable = 1;




    this.initialize = function () {

        _dom.logHead( `binding button actions` );

        //$( 'header' ).text( 'Hi from jQuery!' );

        registerEvents();

    }



    function registerEvents() {

        async function showValuesUsingUtils() {
            showValues();
        }
        //_showValueButton.addEventListener( 'click', showValuesUsingUtils );

        // utils.onClick( _showValueButton,  (showValuesUsingUtils) ) ; 
        
        _dom.onClick( _demo_csap_info_button, () => { showValues( _dialogs.csapInfo ) } );
        _dom.onClick( _demo_csap_warning_button, () => { showValues( _dialogs.csapWarning ) } );

    }

    function showValues( theFunction ) {

        counterVariable++;

        console.log( `showValues: counterVariable ${ counterVariable }` );

        theFunction( ` Hi from module sub-1, times invoked: ${ counterVariable }` );
    }

    this.showValues = showValues;
}

