// Ideas from https://www.labnol.org/code/bundle-react-app-single-file-200514

const path = require('path');
const del = require('del');
const {task, series, src, dest} = require('gulp');
const replace = require('gulp-replace')
const inlineSource = require('gulp-inline-source-html');

task('clean', () => {
    return del('build/gulp-dist', {force:true});
});

task('inline-html', () => {
    return src('build/react-build/*.html')
        .pipe(replace('.js"></script>', '.js" inline></script>'))
        .pipe(replace('rel="stylesheet">', 'rel="stylesheet" inline>'))
        .pipe(inlineSource({compress: false}))
        .pipe(dest('build/gulp-dist'))
})

task('copy-map-files', () => {
    return src('build/react-build/**/*.map')
        .pipe(dest(function (file) {
            // flatten directory structure
            file.path = path.join(file.base, path.basename(file.path));
            return 'build/gulp-dist';
        }))
});

task('default', series('clean', 'inline-html', 'copy-map-files'));