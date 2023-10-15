


import _dom from "../utils/dom-utils.js";

//
// css inserts require absolute path
//
const  FANCY_TREE_PATH =  `${BASE_URL}webjars/fancytree/2.30.0/dist`;
const  DROPZONE_PATH =  `${BASE_URL}js/modules/dropzone`;

/**
 * 
 */
export default  function loadTreeComponents () {

    _dom.logArrow( `loading FileBrowser librarys ` ) ;

    
    if ( typeof Dropzone === 'undefined' ) { 
        _dom.loadCss( `${ FANCY_TREE_PATH }/skin-win7/ui.fancytree.min.css`  );
        _dom.loadCss( `${ DROPZONE_PATH }/dropzone.css`  );
    }

    async function loadModules( targetFm ) {
        await import(  `${ DROPZONE_PATH }/dropzone.js` );
        await import(`${ FANCY_TREE_PATH }/jquery.fancytree-all-deps.js`)
        Dropzone.autoDiscover = false;
    }
    return loadModules( );
}