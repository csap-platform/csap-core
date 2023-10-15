

import  "../../../../webjars/ace-builds/1.4.11/src-noconflict/ace.js"
import  "../../../../webjars/ace-builds/1.4.11/src-noconflict/ext-modelist.js"
import  "../../../../webjars/js-yaml/4.1.0/dist/js-yaml.js"

// console.log( `ace`, ace) ;
export const aceEditor = ace ;
ace.config.set('basePath', `${JS_URL}/../webjars/ace-builds/1.4.11/src-noconflict`) ;

export const jsYaml  = globalThis.jsyaml;



// export aceEditor ;