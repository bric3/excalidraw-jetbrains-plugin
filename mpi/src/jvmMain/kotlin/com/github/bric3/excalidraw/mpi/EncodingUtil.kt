package com.github.bric3.excalidraw.mpi

import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Utility class for JavaScript compatible UTF-8 encoding and decoding.
 *
 * @see [this Stackoverflow answer](https://stackoverflow.com/questions/607176/java-equivalent-to-javascripts-encodeuricomponent-that-produces-identical-output)
 * @author John Topley
 */
object EncodingUtil {
  /**
     * Decodes the passed UTF-8 String using an algorithm that's compatible with
     * JavaScript's `decodeURIComponent` function. Returns
     * `null` if the String is `null`.
     *
     * @param str The UTF-8 encoded String to be decoded
     * @return the decoded String
     */
    fun decodeURIComponent(str: String): String = try {
        URLDecoder.decode(str, "UTF-8")
    } catch (e: UnsupportedEncodingException) {
        str
    }

    /**
     * Encodes the passed String as UTF-8 using an algorithm that's compatible with [standard's JavaScript
     * `encodeURIComponent` function](https://developer.mozilla.org/en/docs/Web/JavaScript/Reference/Global_Objects/encodeURIComponent).
     *
     * @param str The String to be encoded
     * @return the encoded String
     */
    fun encodeURIComponent(str: String): String = try {
        URLEncoder.encode(str, StandardCharsets.UTF_8)
            .replace("+", "%20")
            .replace("%21", "!")
            .replace("%27", "'")
            .replace("%28", "(")
            .replace("%29", ")")
            .replace("%7E", "~")
    } catch (_: UnsupportedEncodingException) {
        str
    }
}