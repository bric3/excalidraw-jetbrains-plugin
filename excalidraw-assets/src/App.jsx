import React from "react";
import Excalidraw, {
    restore,
    restoreAppState,
    restoreElements,
    serializeAsJSON,
    exportToSvg,
    exportToBlob,
    getSceneVersion,
} from "@excalidraw/excalidraw";

// placeholder for functions
// window.loadBlob = null; // not supported yet
window.updateApp = null;
window.exportAsSVG = null;
window.exportAsPNG = null;

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

// let {
//     elements: initialElements = [],
//     appState: initialAppState = {},
//     theme,
//     readOnly
// } = initialData;

let currentSceneVersion = getSceneVersion([]); // scene elements are empty on load


window.addEventListener("message", (e) => {
    const message = e.data;
    console.log("got: " + message.type + ", message: ", message);
    switch (message.type) {
        case "update":
            const {elements} = message;
            let updateSceneVersion = getSceneVersion(elements);
            if (currentSceneVersion !== updateSceneVersion) {
                currentSceneVersion = updateSceneVersion;
                updateApp({
                    elements: elements
                });
            }
            break;

        case "save-as-svg":
            let exportConfig = message.exportConfig;
            console.log(exportConfig);
            let svg = saveAsSvg(exportConfig);
            dispatchToPlugin({
                type: "svg-content",
                svg: svg.outerHTML,
            });
            break;

        case "save-as-png":
            saveAsPng(message.exportConfig).then((blob) => {
                let reader = new FileReader();
                reader.readAsDataURL(blob);
                reader.onloadend = function () {
                    let base64data = reader.result;
                    dispatchToPlugin({
                        type: "png-content",
                        png: base64data
                    });
                };
            });
            break;
    }
});




export default function App() {
    const excalidrawRef = React.useRef(null);

    // DON'T WORK
    // React.useEffect(() => {
    //     excalidrawRef.current
    //         .readyPromise
    //         .then(() => {
    //         dispatchToPlugin("excalidraw-ready")
    //     })
    // }, this);

    // Not Supported yet "Uncaught TypeError: Object(...) is not a function"
    // window.loadBlob = (blob) => {
    //     return loadFromBlob(
    //         blob,
    //         excalidrawRef.current.getAppState());
    // };

    window.updateApp = ({elements, appState}) => {
        excalidrawRef.current.updateScene({
            elements: elements,
            appState: appState,
        });
    };

    // exportParams
    //
    // exportBackground: boolean (true) Indicates whether background should be exported
    // viewBackgroundColor: string (#fff) The default background color
    // exportWithDarkMode: boolean (false) Indicates whether to export with dark mode

    window.saveAsSvg = (exportParams) => {
        return exportToSvg({
            elements: excalidrawRef.current.getSceneElements(),
            appState: {
                ...excalidrawRef.current.getAppState(),
                ...exportParams
            },
        });
    };

    window.saveAsPng = (exportParams) => {
        return exportToBlob({
            elements: excalidrawRef.current.getSceneElements(),
            appState: {
                ...excalidrawRef.current.getAppState(),
                ...exportParams
            },
        });
    };

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
                readyPromise={() => {
                    console.log("excalidraw ready")
                }}
                viewModeEnabled={readOnly}
                zenModeEnabled={zenMode}
                gridModeEnabled={gridMode}
                exportEmbedScene={true}
                theme={theme}
                UIOptions={{canvasActions: {loadScene: false}}}
            />
        </div>
    );
}

function dispatchToPlugin(message) {
    console.log("dispatchToPlugin: ", message);
    if (window.cefQuery) {
        window.cefQuery({
            request: JSON.stringify(message),
            persistent: false,
            onSuccess: function (response) {
                console.log("success for message", message, ", response", response);
            },
            onFailure: function (error_code, error_message) {
                console.log("failure for message", message, ", error_code", error_code, ", error_message", error_message);
            }
        });
    } else {
        console.log(message);
    }
}