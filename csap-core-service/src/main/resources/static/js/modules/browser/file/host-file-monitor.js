
console.log( `loading imports` );


import "../../libs/csap-modules.js";

import { _dialogs, _dom, _utils, _net } from "../../utils/all-utils.js";


import fileMonitor from "./file-monitor.js";

import utils from "../utils.js";


_dom.onReady( function () {

    _dom.logSection( `Starting monitor` );
    fileMonitor.show( $( "body" ), CSAP_HOST_NAME, utils.getParameterByName( "serviceName" ) );


} );
