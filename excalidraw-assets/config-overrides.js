module.exports = function override(config, env) {
    // see https://webpack.js.org/configuration/
    console.log("Disabling build minimizer (config-overrides.js)");
    config.mode = "development";
    config.optimization.minimize = false;
    config.optimization.minimizer = [];

    // https://webpack.js.org/configuration/devtool/
    console.log("Inline SourceMaps (config-overrides.js)");
    config.devtool = 'inline-source-maps'


    return config;
};