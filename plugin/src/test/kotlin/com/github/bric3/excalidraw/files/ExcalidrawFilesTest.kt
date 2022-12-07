package com.github.bric3.excalidraw.files

import com.github.bric3.excalidraw.files.ExcalidrawFiles.Companion.isExcalidrawFile
import com.intellij.testFramework.BinaryLightVirtualFile
import com.intellij.testFramework.LightVirtualFile
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
internal class ExcalidrawFileUtilTest {
    @Test
    fun `should accept svg with embedded excalidraw payload`() {
        @Language("xml") val svgFile =
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
        assertThat(isExcalidrawFile(virtualFile("embedded-excalidraw.svg", svgFile))).isTrue
    }

    @Test
    fun `should discard svg without embedded excalidraw payload`() {
        @Language("xml") val svgFile =
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
            <rect x="0" y="0" width="64" height="46" fill="#ffffff"></rect>
            <g transform="translate(10 10) rotate(0 22 13)">
              <text x="0" y="18" font-family="Virgil, Segoe UI Emoji" font-size="20px" fill="#000000" text-anchor="start" style="white-space: pre;" direction="ltr">Hello</text>
            </g>
            </svg>
            """
        assertThat(isExcalidrawFile(virtualFile("embedded-excalidraw.svg", svgFile))).isFalse
    }

    @Test
    fun `should not accept malformed svg file`() {
        val malformedSvg =
            """
            <svg version="1.1" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 46" width="128" height="92">
            <g transform="translate(10 10) rotate(0 22 13)">
              <text x="0" y="18" font-family="Virgil, Segoe UI Emoji" font-size="20px" fill="#000000" text-anchor="start" style="white-space: pre;" direction="ltr">Hello</text>
            </g>
            </g><!-- error here -->
            </svg>
            """
        assertThat(isExcalidrawFile(virtualFile("malformed.svg", malformedSvg))).isFalse
    }

    @Test
    fun `should accept file with declared excalidraw extension`() {
        assertThat(isExcalidrawFile(virtualFile("sketch.excalidraw", "content ignored"))).isTrue
        assertThat(isExcalidrawFile(virtualFile("sketch.excalidraw.json", "content ignored"))).isTrue
    }

    @Test
    fun `should not accept file with non excalidraw extension`() {
        assertThat(isExcalidrawFile(virtualFile("sketch.psd", "content ignored"))).isFalse
        assertThat(isExcalidrawFile(virtualFile("sketch.java", "content ignored"))).isFalse

        assertThat(isExcalidrawFile(virtualFile("some/directory/sketch.java", "content ignored").parent)).isFalse
    }

    @Test
    fun `should discover excalidraw scene in png`() {
        val virtualFile = BinaryLightVirtualFile(
            "random-with-scene.png",
            null,
            javaClass.classLoader.getResourceAsStream("random-with-scene.png")!!.readAllBytes()
        )
        assertThat(isExcalidrawFile(virtualFile)).isTrue
    }

    @Test
    fun `should accept excalidraw file with json extension`() {
        @Language("json") val excalidrawJsonContent =
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
        assertThat(isExcalidrawFile(virtualFile("sketch.json", excalidrawJsonContent))).isTrue
    }

    @Test
    fun `should not accept non excalidraw json file`() {
        @Language("json") val malformedJson = """ { "whatever": "whatever" }"""
        assertThat(isExcalidrawFile(virtualFile("malformed.json", malformedJson))).isFalse
    }

    @Test
    fun `should not accept malformed json file`() {
        val malformedJson = """ { "malformed_json: "whatever" }"""
        assertThat(isExcalidrawFile(virtualFile("malformed.json", malformedJson))).isFalse
    }

    private fun virtualFile(fileName: String, content: String): LightVirtualFile =
        LightVirtualFile(fileName, content.trimIndent()).also {
            it.charset = StandardCharsets.UTF_8
        }
}
