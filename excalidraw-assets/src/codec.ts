// MIT License
//
// Copyright (c) 2020 Excalidraw
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

// copied from https://github.com/excalidraw/excalidraw/blob/969d3c694aedba0fdf74f8a7f727118655acba84/src/data/encode.ts
// as suggested in https://github.com/excalidraw/excalidraw/discussions/3756#discussioncomment-899556
// keep as long as excalidraw is not able to read or write image file with embedded scene

import {deflate, inflate} from "pako";

// -----------------------------------------------------------------------------
// byte (binary) strings
// -----------------------------------------------------------------------------

// fast, Buffer-compatible implem
export const toByteString = (data: string | Uint8Array): Promise<string> => {
    return new Promise((resolve, reject) => {
        const blob =
            typeof data === "string"
                ? new Blob([new TextEncoder().encode(data)])
                : new Blob([data]);
        const reader = new FileReader();
        reader.onload = (event) => {
            if (!event.target || typeof event.target.result !== "string") {
                return reject(new Error("couldn't convert to byte string"));
            }
            resolve(event.target.result);
        };
        reader.readAsBinaryString(blob);
    });
};

const byteStringToArrayBuffer = (byteString: string) => {
    const buffer = new ArrayBuffer(byteString.length);
    const bufferView = new Uint8Array(buffer);
    for (let i = 0, len = byteString.length; i < len; i++) {
        bufferView[i] = byteString.charCodeAt(i);
    }
    return buffer;
};

const byteStringToString = (byteString: string) => {
    return new TextDecoder("utf-8").decode(byteStringToArrayBuffer(byteString));
};

// -----------------------------------------------------------------------------
// base64
// -----------------------------------------------------------------------------

/**
 * @param isByteString set to true if already byte string to prevent bloat
 *  due to reencoding
 */
export const stringToBase64 = async (str: string, isByteString = false) => {
    return isByteString ? btoa(str) : btoa(await toByteString(str));
};

// async to align with stringToBase64
export const base64ToString = async (base64: string, isByteString = false) => {
    return isByteString ? atob(base64) : byteStringToString(atob(base64));
};

// -----------------------------------------------------------------------------
// text encoding
// -----------------------------------------------------------------------------

type EncodedData = {
    encoded: string;
    encoding: "bstring";
    /** whether text is compressed (zlib) */
    compressed: boolean;
    /** version for potential migration purposes */
    version?: string;
};

/**
 * Encodes (and potentially compresses via zlib) text to byte string
 */
export const encode = async ({
                                 text,
                                 compress,
                             }: {
    text: string;
    /** defaults to `true`. If compression fails, falls back to bstring alone. */
    compress?: boolean;
}): Promise<EncodedData> => {
    let deflated!: string;
    if (compress !== false) {
        try {
            deflated = await toByteString(deflate(text));
        } catch (error) {
            console.error("encode: cannot deflate", error);
        }
    }
    return {
        version: "1",
        encoding: "bstring",
        compressed: !!deflated,
        encoded: deflated || (await toByteString(text)),
    };
};

export const decode = async (data: EncodedData): Promise<string> => {
    let decoded: string;

    switch (data.encoding) {
        case "bstring":
            // if compressed, do not double decode the bstring
            decoded = data.compressed
                ? data.encoded
                : await byteStringToString(data.encoded);
            break;
        default:
            throw new Error(`decode: unknown encoding "${data.encoding}"`);
    }

    if (data.compressed) {
        return inflate(new Uint8Array(byteStringToArrayBuffer(decoded)), {
            to: "string",
        });
    }

    return decoded;
};
