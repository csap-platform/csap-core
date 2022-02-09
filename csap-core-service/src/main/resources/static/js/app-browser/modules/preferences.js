define( [ "browser/utils", "ace/ace" ], function ( utils, aceEditor ) {

    console.log( "Module loaded" ) ;

    let $prefs = $( "#preferences-tab-content" ) ;
    let $inputs = $( "input,select", $prefs ) ;


    let $demoEditorContainer = $( "pre#editor-settings-demo", $prefs ) ;

    const $aceWrapCheckbox = $( "#ace-wrap-checkbox", $prefs ) ;
    const $aceFoldCheckbox = $( "#ace-fold-checkbox", $prefs ) ;
    const $aceTheme = $( "#ace-theme-select", $prefs ) ;
    const $aceFontSize = $( "#ace-font-size", $prefs ) ;

    const $disableYamlFormat = $( "#disable-yaml-format", $prefs ) ;




    let initComplete = false ;

    let _demoEditor ;

    return {

        show: function ( $menuContent, forceHostRefresh, menuPath ) {

            return show( $menuContent, forceHostRefresh, menuPath ) ;

        },

        snapNavClosed: function () {
            snapNavClosed() ;
        },

        initialize: function ( navigationSelector ) {
            initialize( navigationSelector ) ;
            initComplete = true ;
        }

    } ;

    function show( $menuContent, forceHostRefresh, menuPath ) {

        if ( !_demoEditor ) {
            console.log( `building editor` ) ;
            _demoEditor = aceEditor.edit( $demoEditorContainer.attr( "id" ) ) ;
            _demoEditor.setOptions( utils.getAceDefaults( "ace/mode/yaml" ) ) ;
        }

    }

    function initialize() {

        $( "article.navigation #tabs > button.csap-hide-control" ).click( function () {
            $( "#auto-hide-navigation" ).trigger( "click" ) ;

            if ( $( "#auto-hide-navigation" ).prop( "checked" ) ) {
                $( "article.navigation #tabs" ).hide() ;
                setTimeout( function () {
                    $( "article.navigation #tabs" ).show() ;
                }, 1000 ) ;
            }
        } )

        $( "#panel-radius", $prefs ).change( function () {
            console.log( `updating radius` ) ;
            $( "body" ).css( "--border-radius-small", $( "#panel-radius" ).val() ) ;
            updatePreferences() ;
        } ) ;

        $( "#auto-refresh-seconds", $prefs ).change( function () {
            console.log( `updating auto-refresh-seconds` ) ;
            let seconds = parseInt( $( this ).val() ) ;
            utils.setRefreshInterval( seconds ) ;
            updatePreferences() ;

        } ) ;




        $( "#panel-item-max-width", $prefs ).change( function () {
            console.log( `updating panel item max with` ) ;
            $( "body" ).css( "--max-panel-item-width", $( this ).val() ) ;
            updatePreferences() ;
        } ) ;


        $( "#minimum-project-count", $prefs ).change( function () {
            console.log( `updating minimum-project-count` ) ;
            updatePreferences() ;
            let $projectTabNav = utils.findNavigation( "#projects-tab" ) ;
            if ( $( "option", utils.findNavigation( "#active-project" ) ).length > $( "#minimum-project-count", $prefs ).val() ) {
                // All and project name - no point in displaying
                $projectTabNav.addClass( "show-selector" ) ;
            } else {
                $projectTabNav.removeClass( "show-selector" ) ;
            }
        } ) ;

        $( `input[type=checkbox]`, $prefs ).change( function () {
            updatePreferences() ;
        } ) ;


        $( "#restore-defaults" ).click( function () {
            updatePreferences( true ) ;
        } ) ;

        $( "#auto-hide-navigation" ).change( function () {
            console.log( `updating autohide` ) ;
            if ( !$( this ).is( ":checked" ) ) {
                utils.findNavigation().removeClass() ;
                utils.findNavigation().addClass( "navigation" ) ;
            } else {
                utils.findNavigation().addClass( "auto-hide" ) ;
            }
            updatePreferences() ;
        } ) ;

        $( "#csap-theme", $prefs ).change( function () {

            let theme = $( this ).val() ;
            
            let loadTheme = $( "body" ).attr('class') ;
            console.log( `updating theme: ${theme} loadTheme: ${ loadTheme } ` ) ;
            $( "body" ).removeClass() ;

            if ( utils.isAgent() ) {
                
                $( "body" ).addClass( `agent` ) ;
                
                // support docker default theme for agent
                if ( loadTheme.includes( "theme-forest" )  ) {
                    $( "body" ).addClass( `theme-forest` ) ;
                }
                theme = $( `.dark`, $( this ) ).attr( "value" ) ;
                
                $(this).attr("title", "themes are not modifiable on agent") ;
                
                utils.disableButtons( $(this) ) ;
                
            } else 
                if ( theme == "auto" ) {

                let autoType = "deep-blue" ;
                let env = HOST_ENVIRONMENT_NAME ;

                if ( env.includes( "sand" ) ) {
                    autoType = "sand" ;
                } else if ( env.includes( "metal" ) || env.includes( "perf" ) || env.includes( "demo" ) ) {
                    autoType = "metal" ;
                
                } else if ( env.includes( "accept" ) ) {
                    autoType = "apple" ;
                    
                } else if ( env.includes( "test" ) || env.includes( "qa" ) ) {
                    autoType = "tree" ;
                
                } else if ( env.includes( "stage" ) ) {
                    autoType = "sun" ;
                
                } else if ( env.includes( "prod" ) || env.includes( "tenant" ) ) {
                    autoType = "aqua" ;
                
                } else if (  env.includes( "container" )  ) {
                    autoType = "forest" ;
                }
                theme = $( `.${ autoType }`, $( this ) ).attr( "value" ) ;
            }

            $( "body" ).addClass( `csap-scrollable ${ theme }` ) ;
            updatePreferences() ;
        } ) ;

        //
        //  ACE Editor settings
        //
        $aceWrapCheckbox.change( function () {

            utils.updateAceDefaults(
                    $aceWrapCheckbox.is( ":checked" ),
                    $aceTheme.val(),
                    $aceFontSize.val() ) ;

            if ( _demoEditor ) {
                if ( $aceWrapCheckbox.is( ":checked" ) ) {
                    _demoEditor.session.setUseWrapMode( true ) ;

                } else {
                    _demoEditor.session.setUseWrapMode( false ) ;
                }
            }
            updatePreferences( ) ;
        } ) ;

        $aceTheme.change( function () {

            utils.updateAceDefaults(
                    $aceWrapCheckbox.is( ":checked" ),
                    $aceTheme.val(),
                    $aceFontSize.val() ) ;
            if ( _demoEditor ) {
                _demoEditor.setTheme( $( this ).val() ) ;
            }
            updatePreferences( ) ;
        } ) ;

        $aceFontSize.change( function () {

            utils.updateAceDefaults(
                    $aceWrapCheckbox.is( ":checked" ),
                    $aceTheme.val(),
                    $aceFontSize.val() ) ;
            if ( _demoEditor ) {
                _demoEditor.setFontSize( $( this ).val() ) ;
            }
            updatePreferences( ) ;
        } ) ;

        $disableYamlFormat.change( function () {

            $( "#yaml-op-spacing" ).prop( "checked", !$disableYamlFormat.is( ":checked" ) ) ;

            updatePreferences( ) ;
        } ) ;


        //
        //  Update settings based on read values
        //
        if ( PREFERENCES &&
                ( Object.keys( PREFERENCES ).length > 1 ) ) {
            console.log( `Updating preferences `, PREFERENCES ) ;
            for ( let id in PREFERENCES ) {

                let setting = PREFERENCES[ id ] ;
                // console.log( `found id: ${id}, ${setting}` ) ;

                let domId = `#${ id }` ;
                let $pref = $( domId, $prefs ) ;

                if ( $pref.length > 0 ) {
                    if ( $pref.is( ':checkbox' ) ) {
                        $pref.prop( "checked", setting ) ;
                    } else {
                        $pref.val( setting ) ;
                    }
                    $pref.trigger( "change" ) ;
                } else {
                    console.log( `did not find: ${domId}` ) ;
                }

            }
        } else {
            console.log( `no preferences - default theme is auto detect. Checkboxes being reset` ) ;
            $( "#csap-theme" ).trigger( "change" ) ;
        }

        // 


        let $preferenceMenus = $( "#devOps, #tools", $prefs ) ;

        let menuSelectedFunction = function () {
            let targetUrl = $( this ).val() ;

            let $selectedOption = $( "option:selected", $( this ) ) ;
            let isSameWindow = $selectedOption.data( "samewindow" ) == true ;
            console.log( "Selected option: ", $selectedOption.text(),
                    " value: ", targetUrl,
                    " isSameWindow: ", isSameWindow ) ;

            if ( targetUrl != "default" ) {
                console.log( "launching: " + targetUrl ) ;
                if ( isSameWindow || targetUrl.contains( "logout" ) ) {
                    document.location.href = targetUrl ;
                } else {
                    utils.launch( targetUrl ) ;
                }
                $( "header div.csapOptions select" ).val( "default" )
            }

            $preferenceMenus.val( "default" ) ;
            $preferenceMenus.selectmenu( "refresh" ) ;
        }
        //$toolsMenu.change( menuSelectedFunction ) ;
        $preferenceMenus.selectmenu( {
            width: "7em",
            position: { my: "right+45 top+12", at: "bottom right" },
            change: menuSelectedFunction

        } ) ;

    }

    function snapNavClosed() {
        if ( utils.findNavigation().hasClass( "auto-hide" ) && !$( "#auto-hide-snap" ).is( ":checked" ) ) {
            console.log( "hiding tabs" ) ;
            $( "#tabs", utils.findNavigation() ).hide() ;

            setTimeout( function () {
                $( "#tabs", utils.findNavigation() ).show() ;
            }, 500 )
        }
    }


    function updatePreferences( resetDefaults ) {

        console.debug( `updatePreferences() resetDefaults: ${ resetDefaults } ` ) ;

        if ( !initComplete ) {
            return ;
        }



        let preferences = new Object() ;

        if ( resetDefaults ) {
            preferences[ "reset" ] = true ;

            //
            // browser forms will retain - some reset manually
            //
            $inputs.each( function () {
                if ( $( this ).is( ':checkbox' ) ) {

                    if ( $( this ).hasClass( "checked-by-default" ) ) {
                        $( this ).prop( "checked", true ) ;
                    } else {
                        $( this ).prop( "checked", false ) ;
                    }
                }

            } ) ;
        } else {
            $inputs.each( function () {

                let id = $( this ).attr( "id" ) ;
                let val = $( this ).val() ;
                if ( $( this ).is( ':checkbox' ) ) {
                    val = true ;
                    if ( !$( this ).is( ':checked' ) ) {
                        val = false ;
                    }
                }
                preferences[ id ] = val ;

            } ) ;
        }

        let params = {
            preferences: JSON.stringify( preferences )
        }

        $.post( APP_BROWSER_URL + "/preferences", params )

                .done( function ( batchResults ) {
//                    alertify.notify( "preferences stored" ) ;
                    console.log( `preferences updated: `, preferences ) ;
                    if ( resetDefaults ) {

                        window.location.href = "#preferences-tab" ;
                        document.location.reload() ;
                    }
                } )

                .fail( function ( jqXHR, textStatus, errorThrown ) {

                    handleConnectionError( "batchKill", errorThrown ) ;
                } ) ;

        console.log( `preferences: `, preferences ) ;

    }
} ) ;