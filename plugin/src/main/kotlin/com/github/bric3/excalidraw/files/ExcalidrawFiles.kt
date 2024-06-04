package com.github.bric3.excalidraw.files


import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.core.JsonTokenId
import com.fasterxml.jackson.core.StreamReadConstraints
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.bric3.excalidraw.files.ExcalidrawImageType.EXCALIDRAW
import com.github.bric3.excalidraw.files.ExcalidrawImageType.PNG
import com.github.bric3.excalidraw.files.ExcalidrawImageType.SVG
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import org.w3c.dom.NamedNodeMap
import java.io.IOException
import java.io.Reader
import java.nio.charset.StandardCharsets
import java.nio.charset.StandardCharsets.UTF_8
import javax.imageio.ImageIO
import javax.imageio.metadata.IIOMetadata
import javax.xml.stream.XMLEventReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamException
import javax.xml.stream.events.Comment
import javax.xml.stream.events.XMLEvent


object ExcalidrawFiles {
    val EXCALIDRAW_EMBEDDED_SCENE = Key<ByteArray>("application/vnd.excalidraw+json")

    private val logger = logger<ExcalidrawFiles>()

    fun isExcalidrawFile(file: VirtualFile?): Boolean {
        return when {
            file == null -> false
            file.isDirectory || !file.exists() -> false

            // To be aligned with plugin.xml
            arrayOf(".excalidraw", ".excalidraw.json").any { ext -> file.name.endsWith(ext) } -> {
                // assume it is a valid excalidraw file
                file.putUserDataIfAbsent(EXCALIDRAW_IMAGE_TYPE, EXCALIDRAW)
                true
            }

            // check if SVG file has a comment with 'payload-type:application/vnd.excalidraw+json'
            file.name.endsWith(".svg") -> {
                svgHasEmbeddedScene(file).alsoIfTrue {
                    file.putUserDataIfAbsent(EXCALIDRAW_IMAGE_TYPE, SVG)
                }
            }

            // check if PNG file has a tEXt chunk with keyword 'application/vnd.excalidraw+json'
            file.name.endsWith("png") -> {
                pngHasEmbeddedScene(file).alsoIfTrue {
                    file.putUserDataIfAbsent(EXCALIDRAW_IMAGE_TYPE, PNG)
                }
            }


            // check if json file is an excalidraw document
            file.name.endsWith(".json") -> hasTypeExcalidraw(file).alsoIfTrue {
                file.putUserDataIfAbsent(EXCALIDRAW_IMAGE_TYPE, EXCALIDRAW)
            }

            else -> false
        }
    }

    private inline fun Boolean.alsoIfTrue(block: () -> Unit): Boolean {
        block()
        return this
    }

    private fun hasTypeExcalidraw(file: VirtualFile): Boolean = file.inputStream.bufferedReader(UTF_8).use {
        try {
            // We want this code to be fast because we only want to check
            // if the json doc has the type field, other approaches where slower
            // and added memory footprint, the numbers are rough estimate on 74 various
            // huge json payload (fully, i.e. the parser don't stop after 1000 nest levels).
            //
            // - `trustedMapper.readValue<Map<String, Any>>(it)` : 490ms / 594MiB
            // - `trustedMapper.readTree(it)["type"]?.toString()` : 540ms / 226.58MiB
            // - `trustedMapper.readValue<ExcalidrawTypeAccessor>(it).type` : 650ms / 195.12MiB
            // - custom parser (getRootFieldAsString("type")) : 310ms / 5.2MiB
            if (ExcalidrawJsonUtils.getRootFieldAsString(it, "type") == "excalidraw") {
                // Excalidraw can handle malformed document e.g. missing "version", "elements",
                // "appState", etc. So don't check for those fields.
                return@use true
            }
        } catch (ioe: IOException) {
            logger.info("Couldn't read $file", ioe)
        }
        false
    }

    private fun pngHasEmbeddedScene(file: VirtualFile) = tryReadPngExcalidrawScene(file) != null

    private fun svgHasEmbeddedScene(file: VirtualFile): Boolean {
        try {
            file.inputStream.bufferedReader().use {
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
            logger.info("Unable to read this SVG file, maybe it is malformed, file: '$file'", ioe)
        } catch (xse: XMLStreamException) {
            logger.info("Unable to read this SVG file, maybe it is malformed, file: '$file'", xse)
        }
        return false
    }

    private fun tryReadPngExcalidrawScene(
        file: VirtualFile,
    ): ByteArray? {
        try {
            file.inputStream.buffered().use {
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
            logger.info("Unable to read this PNG file, maybe it is malformed, file: '$file'", ioe)
        }

        return null
    }
}

private object ExcalidrawJsonUtils {
    private val trustedMapper = jacksonObjectMapper().apply {
        factory.setStreamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(Short.MAX_VALUE.toInt()).build())

        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    fun getRootFieldAsString(reader: Reader, fieldName: String): String? = trustedMapper.createParser(reader).use { parser ->
        while (!parser.isClosed) {
            val token = parser.nextToken() ?: break

            if (token.id() == JsonToken.FIELD_NAME.id()) {
                if (parser.currentName == fieldName) {
                    parser.nextToken()
                    return parser.valueAsString
                }

                skipValue(parser)
            }
        }
        null
    }

    private fun skipValue(parser: JsonParser) {
        val nextToken = parser.nextToken()

        when(nextToken.id()) {
            JsonTokenId.ID_START_ARRAY -> {
                advanceParser(parser, JsonTokenId.ID_START_ARRAY, JsonTokenId.ID_END_ARRAY)
            }
            JsonTokenId.ID_START_OBJECT -> {
                advanceParser(parser, JsonTokenId.ID_START_OBJECT, JsonTokenId.ID_END_OBJECT)
            }
        }
    }

    private fun advanceParser(parser: JsonParser, startToken: Int, endToken: Int) {
        var depth = 1
        do {
            val next = parser.nextToken()
            if (next.id() == startToken) {
                depth += 1
            } else if (next.id() == endToken) {
                depth -= 1
            }
        } while (depth > 0)
    }
}