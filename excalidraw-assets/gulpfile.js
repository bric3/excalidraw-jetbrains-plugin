// Ideas from https://www.labnol.org/code/bundle-react-app-single-file-200514

const { task, src, dest } = require('gulp');
const replace = require('gulp-replace')
const inlineSource = require('gulp-inline-source-html');

task('default', () => {
    return src('build/*.html')
        .pipe(replace('.js"></script>', '.js" inline></script>'))
        .pipe(replace('rel="stylesheet">', 'rel="stylesheet" inline>'))
        .pipe(inlineSource({ compress: false }))
        .pipe(dest('dist'))
})