// polyfills.js
import 'message-port-polyfill';

import * as React from 'react'
import * as Server from 'react-dom/server'
import * as Babel from '@babel/standalone';

export function ssr(jsxText, props) {
    const compiled = Babel.transform(jsxText, { presets: ['react'] }).code;
    const Component = new Function('React', `return ${compiled}`)(React); // a nicer way to run: eval(compiled);
    const html = Server.renderToString(React.createElement(Component, props));
    
    return html;
}
