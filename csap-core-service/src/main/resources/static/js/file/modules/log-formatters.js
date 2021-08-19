define( [ "browser/utils", "jsYaml/js-yaml" ], function ( utils, yaml ) {


    console.log( "module loaded" ) ;
    const logPadLeft = 14 ;


    const jsonPattern = new RegExp( "^{.*}[ ,\r]*$" ) ;


    return {

        formatLog4j2Output: function ( lineAsJson, isDetails ) {
            return  formatLog4j2Output( lineAsJson, isDetails ) ;
        },

        dumpAndIndentAsYaml: function ( json ) {
            return  dumpAndIndentAsYaml( json ) ;
        },

        simpleFormatter: function ( textContent ) {

            let formattedContent = "" ;
            let outputLines = textContent.split( `\n` ) ;


            for ( let testLine of outputLines ) {

                let formatedLine = testLine ;
                if ( jsonPattern.test( testLine ) ) {
                    try {
                        let lineAsJson = JSON.parse( testLine ) ;

                        if ( lineAsJson.loggerFqcn ) {
                            formatedLine = formatLog4j2Output( lineAsJson ) ;
                        } else {

                            formatedLine = "" ;
                            if ( lineAsJson.level ) {
                                formatedLine += "--- " + lineAsJson.level ;
                                delete lineAsJson.level ;
                            }
                            if ( lineAsJson.timestamp ) {
                                if ( formatedLine.length > 0 ) {
                                    formatedLine += "\n" ;
                                }
                                formatedLine += "\t\ttime: " + lineAsJson.timestamp ;
                                delete lineAsJson.timestamp ;
                            }
                            formatedLine += dumpAndIndentAsYaml( lineAsJson ) ;
                        }

                    } catch ( err ) {
                        console.debug( `Failed parsing json line: '${ textContent}'`, err ) ;
                    }
                } else if ( testLine.length === 0
                        || testLine.charAt( 0 ) === " "
                        || testLine.charAt( 0 ) === "\n"
                        || testLine.charAt( 0 ) === "\t" ) {
                    // ignore it
                } else {
                    // double space lines for readability
                    formatedLine = "\n" ;

                    let fields = utils.stringSplitWithRemainder( testLine, " ", 5 ) ;
                    //console.log( `fields.length ${fields.length }` )

                    if ( fields.length == 5 ) {
                        formatedLine += "--- " + fields[0] + "  " + fields[1] + "  " + fields[2] ;
                        
                        //
                        // if field three includes an expression closure - include it in the header line
                        //
                        if ( fields[3].includes("]") 
                                || fields[3].includes("|") ) {
                            formatedLine += "  " + fields[3] 
                                + "\n" + "\t\t" + fields[4] ;
                        } else {
                            formatedLine += "\n" + "\t\t" + fields[3] + "  " + fields[4];
                        }
                    } else {
                        formatedLine += testLine ;
                    }


                }
                formattedContent += "\n" + formatedLine ;

            }

            return formattedContent ;
        },

    }

    function formatLog4j2Output( lineAsJson, isDetails ) {
        let timestamp = lineAsJson.timeMillis ;
        if ( lineAsJson.friendlyDate ) {
            timestamp = lineAsJson.friendlyDate ;
        }

        //console.log( `formatLog4j2Output: ${ timestamp } ` ) ;

        let classPath = lineAsJson.loggerFqcn ;
        if ( lineAsJson.source ) {
            classPath = lineAsJson.source.class ;
//            if ( subsystem.length > 20 ) {
//                subsystem = subsystem.substr( subsystem.length - 20) ;
//            }
        }
        if ( !classPath ) {
            classPath = "unknown" ;
        }
        let logLevel = lineAsJson.level ;
        if ( !logLevel ) {
            logLevel = "unknown" ;
        }

        let outputText = `\n--- # ${ logLevel }\n`
//        let outputText = "\n---\n" + padLeft( "level" ) + `${ logLevel }\n`


        outputText += padLeft( "time:" ) + ` ${ timestamp }\n` ;
        if ( lineAsJson.source ) {

            let packageSummary = lineAsJson.source.class ;
            let sourcePackages = packageSummary.split( "." ) ;
            if ( sourcePackages.length > 3 ) {
                packageSummary = `${ sourcePackages[0] }.${ sourcePackages[1] }.${ sourcePackages[2] }.<>.` ;
            }
            outputText += padLeft( "source:" ) + " " + packageSummary
                    + `${ lineAsJson.source.file}:${ lineAsJson.source.line } ${ lineAsJson.source.method }()\n`
        } else {
            outputText += padLeft( "class:" ) + ` ${ classPath }\n` ;
        }

        if ( isDetails ) {
            outputText += padLeft( "thread:" ) + ` ${ lineAsJson.thread }\n`

            if ( lineAsJson.source ) {
                outputText += padLeft( "class:" ) + ` ${ classPath }\n` ;
            }
        }

        let message = lineAsJson.message ;
        let newLineWords = [ "{", "[" ] ;
//        let keywords = [ ] ;
        for ( let keyword of newLineWords ) {
            message = message.replaceAll( `${ keyword }`, `\n${ padLeft( keyword )}` ) ;
        }
        outputText += padLeft( "message:" ) + ` ${ message }` ;

        return  outputText ;
    }


    function padLeft( message ) {
        if ( !message.endsWith( ":" ) ) {
            return  " ".padStart( logPadLeft ) + " " + message ;
        } else {
            return  message.padStart( logPadLeft ) ;
        }
    }


    function dumpAndIndentAsYaml( json ) {
        return "\n\t\t" + yaml.dump( json ).replaceAll( "\n", "\n\t\t" ) + "\n\n" ;
    }

} ) ;