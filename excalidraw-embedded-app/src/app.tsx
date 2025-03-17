// excalidraw library with public API
import * as excalidrawLib from "@excalidraw/excalidraw";
// excalidraw react component
import { Excalidraw } from "@excalidraw/excalidraw";
// excalidraw styles, usually auto-processed by the build tool (i.e. vite, next, etc.)
import "@excalidraw/excalidraw/index.css";
// excalidraw types (optional)
import type { ExcalidrawImperativeAPI } from "@excalidraw/excalidraw/types";

export function App() {
    return (
            <>
                <h1 style={{ textAlign: "center" }}>Excalidraw Example</h1>
                <div style={{ height: "500px" }}>
                    <Excalidraw />
                </div>
            </>
    );
}