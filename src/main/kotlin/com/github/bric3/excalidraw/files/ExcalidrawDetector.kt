package com.github.bric3.excalidraw.files

import com.intellij.openapi.vfs.VirtualFile
import java.io.InputStreamReader
import javax.xml.stream.XMLEventReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.events.Comment
import javax.xml.stream.events.XMLEvent


class ExcalidrawDetector private constructor() {
    companion object {
        fun isExcalidrawFile(file: VirtualFile?): Boolean {
            if (file == null) {
                return false
            }

            if (file.isDirectory || !file.exists()) {
                return false
            }
            //check for the right file extension
            val extensions = arrayOf(".excalidraw", ".excalidraw.svg", ".excalidraw.json")
            // Short-circuit for well-known file names. Allows to start with an empty file and open it in the editor.
            if (extensions.any { ext -> file.name.endsWith(ext) }) {
                return true
            }

            // check if svg file has a comment with 'payload-type:application/vnd.excalidraw+json'
            if (file.name.endsWith(".svg")) {
                val factory: XMLInputFactory = XMLInputFactory.newInstance()

                file.inputStream.use {
                    val eventReader: XMLEventReader = factory.createXMLEventReader(InputStreamReader(it))
                    while (eventReader.hasNext()) {
                        val event: XMLEvent = eventReader.nextEvent()

                        if (event.eventType == XMLStreamConstants.COMMENT) {
                            val text: String =  (event as Comment).text
                            if(text.contains("payload-type:application/vnd.excalidraw+json")) {
                                return true
                            }
                        }
                    }
                }


//                val factory = DocumentBuilderFactory.newInstance()
//                val builder = factory.newDocumentBuilder()
//                // check if file has a comment with 'payload-type:application/vnd.excalidraw+json'
//                file.inputStream.use {
//                    val doc = builder.parse(it)
//                    val xPathfactory = XPathFactory.newInstance()
//                    val xpath = xPathfactory.newXPath()
//                    val expr = xpath.compile("/svg/@content")
//                    val content = expr.evaluate(doc, XPathConstants.STRING)
//                    if (content.toString().startsWith("<mxfile ")) {
//                        return true
//                    }
//                }
            }

            // check if json file is an excalidraw document
            if (file.name.endsWith(".json")) {
                // parse json
            }

            return false
        }
    }
}