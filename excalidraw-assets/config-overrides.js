// part of 'react-app-rewired' to disable minification
// overrides the react-script config file found here
// ./node_modules/react-scripts/config/webpack.config.js

module.exports = function override(config, env) {
    // see https://webpack.js.org/configuration/
    console.log("config-overrides.js: Disabling build minimizer");
    config.mode = "development";
    config.optimization.minimize = false;
    config.optimization.minimizer = [];

    // https://webpack.js.org/configuration/devtool/
    console.log("config-overrides.js: Set devtool inline SourceMaps");
    config.devtool = "inline-source-map"

    // There seem to be a bug in CRA 5 that causes warnings about source maps
    // Disable the warning for now
    // Discussion of the bug: https://github.com/facebook/create-react-app/discussions/11767
    // PR supposed to fix the issue: https://github.com/facebook/create-react-app/pull/11752
    console.log("config-overrides.js: Ignoring source map warning https://github.com/facebook/create-react-app/discussions/11767");
    config.ignoreWarnings = [/Failed to parse source map/];

    return config;
};
