package com.github.bric3.excalidraw.files


import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import org.w3c.dom.NamedNodeMap
import java.io.IOException
import java.nio.charset.StandardCharsets
import javax.imageio.ImageIO
import javax.imageio.metadata.IIOMetadata
import javax.xml.stream.XMLEventReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException
import javax.xml.stream.events.Comment
import javax.xml.stream.events.XMLEvent


class ExcalidrawFileUtil private constructor() {
    companion object {
        val EXCALIDRAW_EMBEDDED_SCENE = Key<ByteArray>("application/vnd.excalidraw+json")

        private val logger = logger<ExcalidrawFileUtil>()
        
        private val mapper = jacksonObjectMapper().apply {
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }


        fun isExcalidrawFile(file: VirtualFile?): Boolean {
            return when {
                file == null -> false
                file.isDirectory || !file.exists() -> false

                // To be aligned with plugin.xml
                arrayOf(".excalidraw", ".excalidraw.json").any { ext -> file.name.endsWith(ext) } -> true

                // check if SVG file has a comment with 'payload-type:application/vnd.excalidraw+json'
                file.name.endsWith(".svg") -> {
                    svgHasEmbeddedScene(file)
                }

                // check if PNG file has a tEXt chunk with keyword 'application/vnd.excalidraw+json'
                file.name.endsWith("png") -> {
                    pngHasEmbeddedScene(file)
                }


                // check if json file is an excalidraw document
                file.name.endsWith(".json") -> {
                    file.inputStream.use {
                        try {
                            val excalidraw = mapper.readValue<Map<String, Any>>(it)
                            if (excalidraw["type"] as String? != "excalidraw") {
                                return false
                            }

                            // Excalidraw can handle malformed document eg missing "version", "elements", "appState"
                            true
                        } catch (e: IOException) {
                            false
                        }

                    }
                }

                else -> false
            }
        }

        private fun pngHasEmbeddedScene(file: VirtualFile) = tryReadPngExcalidrawScene(file) != null

        private fun svgHasEmbeddedScene(file: VirtualFile): Boolean {
            try {
                file.inputStream.reader().use {
                    val factory: XMLInputFactory = XMLInputFactory.newInstance()
                    val eventReader: XMLEventReader = factory.createXMLEventReader(it)
                    while (eventReader.hasNext()) {
                        val event: XMLEvent = eventReader.nextEvent()

                        if (event.eventType == XMLStreamConstants.COMMENT) {
                            val text: String = (event as Comment).text
                            if (text.contains("payload-type:application/vnd.excalidraw+json")) {
                                return true
                            }
                        }
                    }
                }
            } catch (ioe: IOException) {
                logger.warn("Unable to read this SVG file, maybe it is malformed, file: '$file'", ioe)
            } catch (xse: XMLStreamException) {
                logger.warn("Unable to read this SVG file, maybe it is malformed, file: '$file'", xse)
            }
            return false
        }

        private fun tryReadPngExcalidrawScene(
            file: VirtualFile,
        ): ByteArray? {
            try {
                file.inputStream.use {
                    val ir = ImageIO.getImageReadersBySuffix("png").next()
                    ir.setInput(ImageIO.createImageInputStream(it), true)
                    val imageMetadata: IIOMetadata = ir.getImageMetadata(0)
                    val tree = imageMetadata.getAsTree(imageMetadata.nativeMetadataFormatName)
                    val childNodes = tree.childNodes

                    for (i in 0 until childNodes.length) {
                        val node = childNodes.item(i)
                        if (node.nodeName == "tEXt") {
                            val tEXtChildNodes = node.childNodes
                            for (j in 0 until tEXtChildNodes.length) {
                                val tEXtNode = tEXtChildNodes.item(j)
                                if (tEXtNode.nodeName == "tEXtEntry") {
                                    val attributes: NamedNodeMap = tEXtNode.attributes
                                    if (attributes.getNamedItem("keyword")?.nodeValue == "application/vnd.excalidraw+json") {
                                        return attributes.getNamedItem("value")?.nodeValue?.toByteArray(StandardCharsets.ISO_8859_1)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (ioe: IOException) {
                logger.warn("Unable to read this PNG file, maybe it is malformed, file: '$file'", ioe)
            }

            return null
        }
    }
}
