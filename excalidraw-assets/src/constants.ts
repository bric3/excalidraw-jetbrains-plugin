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

// adapted from https://github.com/excalidraw/excalidraw/blob/9cc741ab3a2a56726035c3d8f0a358c8dd91fae1/src/constants.ts
// as suggested in https://github.com/excalidraw/excalidraw/discussions/3756#discussioncomment-899556
// keep as long as excalidraw is not able to read or write image file with embedded scene

export const MIME_TYPES = {
    excalidraw: "application/vnd.excalidraw+json",
    excalidrawlib: "application/vnd.excalidrawlib+json",
};

export const EXPORT_DATA_TYPES = {
    excalidraw: "excalidraw",
    excalidrawClipboard: "excalidraw/clipboard",
    excalidrawLibrary: "excalidrawlib",
} as const;
