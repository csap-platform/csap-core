// define( [ "services/instances", "browser/utils", "ace/ace", "ace/ext-modelist" ], function ( instances, utils, aceEditor, aceModeListLoader ) {

//     console.log( "Module loaded" ) ;




import _dom from "../../utils/dom-utils.js";



import utils from "../utils.js"


import instances from "./instances.js"
import { aceEditor, jsYaml } from "../../libs/file-editor.js"

const svcHelm = service_helm();

export default svcHelm


function service_helm() {

    _dom.logHead( "Module loaded" );

    const $readmePanel = utils.findContent( "#services-tab-helm-readme" ) ;
    const $helmReadmeEditor = $( "#helm-readme-viewer", $readmePanel ) ;
    const $readmeSource = $( "#readme-source", $readmePanel ) ;

    const $valuesPanel = utils.findContent( "#services-tab-helm-values" ) ;
    const $helmValuesEditor = $( "#helm-values-editor", $valuesPanel ) ;
    const $helmShowAll = $( "#helm-show-all", $valuesPanel ) ;
    const $helmFold = $( "#helm-fold", $valuesPanel ) ;

    let _helmValuesEditor ;

    return {

        readme: function ( $menuContent, forceHostRefresh, menuPath ) {

            console.log( `details for ${ menuPath }` ) ;

            initialize() ;

            return loadAttributeDetails( menuPath ) ;

        },

        values: function ( $menuContent, forceHostRefresh, menuPath ) {

            console.log( `details for ${ menuPath }` ) ;

            initialize( ) ;

            return loadAttributeDetails( menuPath ) ;

        }

    } ;

    function initialize(  ) {


        if ( !_helmValuesEditor ) {

            let editorId = $helmValuesEditor.attr( "id" ) ;

            console.log( ` Building: _helmValuesEditor editor: ${ editorId } ` ) ;
            _helmValuesEditor = aceEditor.edit( editorId ) ;
            _helmValuesEditor.setOptions( utils.getAceDefaults( "ace/mode/yaml", true ) ) ;
//            _helmValuesEditor.setTheme( "ace/theme/merbivore_soft" ) ;
            _helmValuesEditor.setTheme( "ace/theme/chrome" ) ;

            let $aceWrapCheckbox = $( '#helm-wrap', $valuesPanel ) ;
            $aceWrapCheckbox.change( function () {
                if ( $( this ).is( ':checked' ) ) {
                    _helmValuesEditor.session.setUseWrapMode( true ) ;

                } else {
                    _helmValuesEditor.session.setUseWrapMode( false ) ;
                }
            } ) ;

            $helmFold.change( function () {
                if ( $( this ).is( ':checked' ) ) {
                    _helmValuesEditor.getSession().foldAll( 1 ) ;
                } else {
                    //_yamlEditor.getSession().unfoldAll( 2 ) ;
                    _helmValuesEditor.getSession().unfold( ) ;
                }
            } ) ;

            $helmShowAll.change( function () {
                loadAttributeDetails( "helm-values" ) ;
            } )

        }

    }



    function loadAttributeDetails( menuPath ) {

        let $contentLoaded = new $.Deferred() ;
        utils.loading( `Loading` ) ;
        let selectedService = instances.getSelectedService() ;



        if ( selectedService === "not-initialized-yet" ) {
            utils.launchMenu( "services-tab,status" ) ;
            return ;
        }

        let parameters = {
            chart: selectedService,
            project: utils.getActiveProject(),
            command: menuPath,
            showAll: $helmShowAll.is( ":checked" )
        } ;


        let helmUrl = `${ APP_BROWSER_URL }/helm/info` ;


        let latestReport = instances.getLatestReport() ;
        if ( latestReport.readme ) {
            parameters = {
                name: selectedService
            }
            helmUrl = `${ APP_BROWSER_URL }/readme` ;
        }

        console.log( `loading: ${ helmUrl }`, parameters ) ;

        $.getJSON( helmUrl, parameters )

                .done( function ( itemReport ) {
                    // console.log( ` content: `, lifeDialogHtml ) ;

                    utils.loadingComplete() ;
                    //alertify.csapInfo( eventDetails["response-yaml"]  ) ;
                    //addPodDetails( podDetails, $container ) ;
                    updateEditor( itemReport, menuPath ) ;

                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    utils.loadingComplete() ;
                    handleConnectionError( `Getting chart details ${ menuPath }`, errorThrown ) ;
                } ) ;






        return $contentLoaded ;


    }

    function updateEditor( itemReport, menuPath ) {

        if ( menuPath === "helm-values" 
                && itemReport["response-yaml"] ) {
            
            _helmValuesEditor.getSession().setValue( itemReport["response-yaml"] ) ;
            
        } else {
            
            console.log( `itemReport: `, itemReport) ;
            $helmReadmeEditor.html( itemReport["response-html"] ) ;

            if ( itemReport.source ) {
                $readmeSource.empty() ;
                let label = itemReport.source ;
                if ( label.length > 20 ) {
                    label = label.substring(0,20) + "..." ;
                }
                if ( itemReport.source.startsWith( "http" ) ) {
                    jQuery( '<a/>', {
                        href: itemReport.source,
                        title: itemReport.source,
                        target: "_blank", 
                        text: label,
                        class: "csap-link-icon csap-window"
                    } ).appendTo( $readmeSource ) ;
                } else {
                    $readmeSource.text( itemReport.source ) ;
                }
            }

        }


        //$( ".code-fold", $container ).trigger( "change" ) ;


    }


}

