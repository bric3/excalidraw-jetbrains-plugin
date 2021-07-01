<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# excalidraw-jetbrains-plugin Changelog

## [Unreleased]
### Changed
- The Excalidraw webapp is now written in typescript
- The Excalidraw webapp now inline sourcemaps to enable 
  debugging within the JCEF DevTools, this was impossible due to #7

## [0.2.0-eap] - 2021-07-01
### Added
- Save drawing to SVG or PNG (without the Excalidraw scene metadata)
- Options when saving to image (background color, dark mode, watermark)
- Options to display the grid or to toggle the zen mode

### Fixed
- Fixed a bug that prevented to open JSON files, the code is trying to identify Excalidraw JSON files

## [0.1.0-eap] - 2021-06-25
### Added
- Initial support of Excalidraw integration in JetBrains IDEs :
  create, open (with autosave) `.excalidraw` (json) files. 
