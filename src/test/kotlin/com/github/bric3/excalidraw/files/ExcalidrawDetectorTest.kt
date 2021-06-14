package com.github.bric3.excalidraw.files

import com.intellij.mock.MockVirtualFileSystem
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

class ExcalidrawDetectorTest : BasePlatformTestCase() {
    @Test
    fun should_accept_svg_with_embedded_excalidraw_payload() {
        assertThat(
            ExcalidrawDetector.isExcalidrawFile(
                lvfOf(
                    "embedded-excalidraw.svg",
                    """
                        <svg version="1.1" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 46" width="128" height="92">
                        <!-- svg-source:excalidraw -->
                        <!-- payload-type:application/vnd.excalidraw+json --><!-- payload-version:2 --><!-- payload-start -->eyJ2ZXJzaW9uIjoiMSIsImVuY29kaW5nIjoiYnN0cmluZyIsImNvbXByZXNzZWQiOnRydWUsImVuY29kZWQiOiJ4nGVSyU7DMFx1MDAxML3zXHUwMDE1kbmyJCVd4FbKKiQkVCEkXHUwMDEwXHUwMDA3k0xcdTAwMTOLqW3ZXHUwMDEz2oL4d2ynxKH4YGnebG/ezNdekjDaaGBnXHSDdcFRlIav2IHHP8BYoaRzXHKCbVVjilx1MDAxMFlcdTAwMTNpe3Z8XHUwMDFjM45cbrVss1x1MDAwMGFcdJKsi3txdpJ8hd95ROlz76aj6+dy+Fx1MDAwNlx1MDAwZlirw1x1MDAxM13cPTazkFx1MDAxYYJ+yVx1MDAxMKwpomtcdTAwMDeN01Fnb5ydXHKzzl6JkmqH5XlcdTAwMDfVIKqaPPmYxmWFvnraIZaMeoeZQmV81/00vNj4jVx1MDAxN++VUY0su1x1MDAxODJcXFrNjVx1MDAxYjLGLVx1MDAwNOKcNtjKw4u6McB2ujxtSWY7eJdnlVx1MDAxMzNmubZVLcHaPzlK80JQXHUwMDEwII1zeI76tlxmqr/u1q+50ds6zHqjx1xmwG9lPFx1MDAxOI5O8skgXHUwMDE2jMtcdTAwMWbvYvdKhjPITvM8XHUwMDFi5JM0ii7shds/hZpcdTAwMGKOXHUwMDE2opBew8v2NlqeskGMa/frdvRuXHUwMDAwUfVkVZLm4tN363Hz6Fx1MDAxNV9cbtz8UcbXmKKoPGmGsOitxzEn4W61c5PS/SVbQCHDTJNcdTAwMDB+uz/IyLjWc+Lkfe0hO6lFueVcdTAwMTRHYFx1MDAxZlx1MDAwMlbn/49lf1x1MDAxMVx1MDAxZdvzNb9/XHUwMDAw4FnnXG4ifQ==<!-- payload-end -->
                        <defs>
                          <style>
                            @font-face {
                              font-family: "Virgil";
                              src: url("https://excalidraw.com/Virgil.woff2");
                            }
                            @font-face {
                              font-family: "Cascadia";
                              src: url("https://excalidraw.com/Cascadia.woff2");
                            }
                          </style>
                        </defs>
                        <rect x="0" y="0" width="64" height="46" fill="#ffffff"></rect><g transform="translate(10 10) rotate(0 22 13)"><text x="0" y="18" font-family="Virgil, Segoe UI Emoji" font-size="20px" fill="#000000" text-anchor="start" style="white-space: pre;" direction="ltr">Hello</text></g></svg>
                    """
                )
            )
        ).isTrue
    }

    @Test
    fun should_discard_svg_without_embedded_excalidraw_payload() {
        assertThat(
            ExcalidrawDetector.isExcalidrawFile(
                lvfOf(
                    "embedded-excalidraw.svg",
                    """
                        <svg version="1.1" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 46" width="128" height="92">
                        <defs>
                          <style>
                            @font-face {
                              font-family: "Virgil";
                              src: url("https://excalidraw.com/Virgil.woff2");
                            }
                            @font-face {
                              font-family: "Cascadia";
                              src: url("https://excalidraw.com/Cascadia.woff2");
                            }
                          </style>
                        </defs>
                        <rect x="0" y="0" width="64" height="46" fill="#ffffff"></rect><g transform="translate(10 10) rotate(0 22 13)"><text x="0" y="18" font-family="Virgil, Segoe UI Emoji" font-size="20px" fill="#000000" text-anchor="start" style="white-space: pre;" direction="ltr">Hello</text></g></svg>
                    """
                )
            )
        ).isFalse
    }

    @Test
    fun should_accept_file_with_excalidraw_extension() {
        assertThat(
            ExcalidrawDetector.isExcalidrawFile(
                lvfOf(
                    "sketch.excalidraw",
                    """
                        {
                          "type": "excalidraw",
                          "version": 2,
                          "source": "https://excalidraw.com",
                          "elements": [
                            {
                              "id": "iXnxxJATdZI9GNSKXAq5o",
                              "type": "text",
                              "x": 774,
                              "y": 158.5,
                              "width": 44,
                              "height": 26,
                              "angle": 0,
                              "strokeColor": "#000000",
                              "backgroundColor": "transparent",
                              "fillStyle": "hachure",
                              "strokeWidth": 1,
                              "strokeStyle": "solid",
                              "roughness": 1,
                              "opacity": 100,
                              "groupIds": [],
                              "strokeSharpness": "sharp",
                              "seed": 415262735,
                              "version": 7,
                              "versionNonce": 1396575855,
                              "isDeleted": false,
                              "boundElementIds": null,
                              "text": "Hello",
                              "fontSize": 20,
                              "fontFamily": 1,
                              "textAlign": "left",
                              "verticalAlign": "top",
                              "baseline": 18
                            }
                          ],
                          "appState": {
                            "gridSize": null,
                            "viewBackgroundColor": "#ffffff"
                          }
                        }
                    """
                )
            )
        ).isTrue
    }

    private fun lvfOf(fileName: String, content: String): LightVirtualFile {
        val vfs = MockVirtualFileSystem().file(
            fileName,
            content.trimIndent()
        )

        return (vfs.findFileByPath(fileName) as LightVirtualFile).also {
            it.charset = StandardCharsets.UTF_8
        }
    }
}