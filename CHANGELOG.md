<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# excalidraw-jetbrains-plugin Changelog

## [Unreleased]

Same as 0.4.1, 2024.3 compatibility, disable off-screen rendering by default, Excalidraw 0.17.6 (website runs unreleased version).

### Fixed

- Unset system properties used for development prevented excalidraw to load. 

## 0.4.1 - 2024-11-10

- Note: At this time [excalidraw.com](https://excalidraw.com) uses an unreleased version of
   [excalidraw/excalidraw](https://github.com/excalidraw/excalidraw), unfortunately, the exported files may not render 
   correctly in IDE plugin that use a released version of excalidraw.

   The next [Excalidraw release should be v18](https://github.com/excalidraw/excalidraw/issues/8771). 
   This plugin uses version is 0.17.6.

   See [#342](https://github.com/bric3/excalidraw-jetbrains-plugin/issues/342) for workarounds

### Changed

- Use the new `org.jetbrains.intellij.platform` v2.0.0 Gradle plugin to build the Excalidraw plugin
- Don't use offscreen rendering (OSR) by default due to issues with trackpad (at least on macOs), see  
   https://youtrack.jetbrains.com/issue/IJPL-156342/Scrolling-of-the-Whats-new-in-IntelliJ-IDEA-is-too-fast-faster-than-code-files

  This is tunable in the advanced settings. [#364](https://github.com/bric3/excalidraw-jetbrains-plugin/issues/364)
- Plugin compatibility with 2024.3

## 0.4.0 - 2024-01-27

### Added

- Toggle between light and dark separated from the current theme by [@michael-pratt](https://github.com/michael-pratt), see [#92](https://github.com/bric3/excalidraw-jetbrains-plugin/pull/92)
- Supports scratch files [#90](https://github.com/bric3/excalidraw-jetbrains-plugin/issues/90) [#22](https://github.com/bric3/excalidraw-jetbrains-plugin/issues/22)
- Exports to JPG and WEBP [#69](https://github.com/bric3/excalidraw-jetbrains-plugin/issues/69)

### Changed

- New plugin icon, matching the new excalidraw icon
- Upgraded excalidraw to version 0.17.0
- Opens Excalidraw editor rather than JSON by [@michael-pratt](https://github.com/michael-pratt), see [#93](https://github.com/bric3/excalidraw-jetbrains-plugin/pull/93)
- Switched to offscreen rendering [#17](https://github.com/bric3/excalidraw-jetbrains-plugin/issues/17)
- Bumped the minimum platform to 2023.2

### Fixed

- Fixed various errors by rewriting the saving mechanism (now using kotlin coroutines), to fix asynchronous writes.
- Saving and Continuous saving doesn't work with images with embedded excalidraw. [#70](https://github.com/bric3/excalidraw-jetbrains-plugin/issues/70)
  Unfortunately Excalidraw files with embedded images cannot be loaded due to [excalidraw/excalidraw#7553](https://github.com/excalidraw/excalidraw/discussions/7553).
- Workaround for Excalidraw issue when exporting to SVG files, the font links are incorrect, see [excalidraw/excalidraw#7543](https://github.com/excalidraw/excalidraw/issues/7543).

## 0.3.2 - 2021-09-14

### New

- This version is the first public release of the excalidraw integration.
  
  This version supports the edition of `.excalidraw`, and embedded excalidraw
  in `svg` and `png` image files. This plugin is engineered to be able to work 
  offline, consequently online collaboration is not supported.
  
  Contributions are welcome: Excalidraw library support, plugin settings, switch 
  to [excalidraw.com](https://excalidraw.com) (thus enabling collaboration), etc.
  Please check out the [project](https://github.com/bric3/excalidraw-jetbrains-plugin).

## 0.3.2-eap - 2021-09-11

### Fixed

- Incorrect editor selected in split panes ([#11](https://github.com/bric3/excalidraw-jetbrains-plugin/issues/13))

## 0.3.1-eap - 2021-08-12

### Fixed

- Fixed compatibility issue with 2021.1.x

## 0.3.0-eap - 2021-08-11

### Added

- SVG and PNG exports are now saved with an embedded scene (always on) ([#11](https://github.com/bric3/excalidraw-jetbrains-plugin/issues/11))
- SVG and PNG files an embedded scene are recognized and can be loaded in the editor ([#12](https://github.com/bric3/excalidraw-jetbrains-plugin/issues/12))
- Modified excalidraw files marker if _Mark Modified (*)_ is ticked (in _Preferences | Editor | General | Editor Tabs_)

### Changed

- The Excalidraw webapp is now written in typescript
- The Excalidraw webapp now inline sourcemaps to enable 
  debugging within the JCEF DevTools, this was impossible due to a bug in chromium
  (see [#7](https://github.com/bric3/excalidraw-jetbrains-plugin/issues/7))
- Refactored how editor are auto-saved, now using IntelliJ's built-in mechanism.

## 0.2.0-eap - 2021-07-01

### Added

- Save drawing to SVG or PNG (without the Excalidraw scene metadata) ([#3](https://github.com/bric3/excalidraw-jetbrains-plugin/issues/3))
- Options when saving to image (background color, dark mode, watermark) ([#9](https://github.com/bric3/excalidraw-jetbrains-plugin/issues/9))
- Options to display the grid or to toggle the zen mode ([#8](https://github.com/bric3/excalidraw-jetbrains-plugin/issues/8))

### Fixed

- Fixed a bug that prevented to open JSON files, the code is trying to identify Excalidraw JSON files (#5)

## 0.1.0-eap - 2021-06-25

### Added

- Initial support of Excalidraw integration in JetBrains IDEs :
  create, open (with autosave) `.excalidraw` (json) files.
