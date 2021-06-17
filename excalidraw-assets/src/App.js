import React from "react";
import Excalidraw from "@excalidraw/excalidraw";

// import Excalidraw, {
//     exportToSvg,
//     exportToBlob,
//     getSceneVersion,
// } from "@excalidraw/excalidraw";

// placeholder for functions
let updateApp = null;
let toSVG = null;
let toPNG = null;

const defaultInitialData = {
    readOnly: false,
    gridMode: true,
    zenMode: false,
    theme: "light",
}
const initialData = window.initialData ? window.initialData : defaultInitialData;

let {
    readOnly,
    gridMode,
    zenMode,
    theme
} = initialData

export default function App() {
    const excalidrawRef = React.useRef(null);

    return (
        <div className="excalidraw-wrapper">
            <Excalidraw
                ref={excalidrawRef}
                // initialData={InitialData}
                // initialData={{ elements: initialElements, appState: initialAppState, libraryItems: libraryItems }}
                // UIOptions={{ canvasActions: { clearCanvas: false, export: false, loadScene: false, saveScene: false } }}
                onChange={(elements, state) =>
                    console.log("Elements :", elements, "State : ", state)
                }
                onCollabButtonClick={() =>
                    window.alert("Not supported")
                }
                viewModeEnabled={readOnly}
                zenModeEnabled={zenMode}
                gridModeEnabled={gridMode}
                theme={theme}
                UIOptions={{ canvasActions: { loadScene: false } }}
            />
        </div>
    );
}