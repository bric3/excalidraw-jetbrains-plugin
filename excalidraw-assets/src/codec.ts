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

import {deflate} from "pako";

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
