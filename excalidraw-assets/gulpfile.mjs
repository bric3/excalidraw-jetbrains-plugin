// Ideas from https://www.labnol.org/code/bundle-react-app-single-file-200514

import path from 'path';
import { deleteAsync } from 'del';
import gulp from 'gulp';
const { series, parallel, src, dest, task } = gulp;

import replace from "gulp-replace";
import inlineSource from 'gulp-inline-source-html';

task('clean', () => {
    return deleteAsync('build/gulp-dist', {force:true});
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