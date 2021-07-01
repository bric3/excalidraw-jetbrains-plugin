import React from "react";
import Excalidraw, {exportToBlob, exportToSvg, getSceneVersion, serializeAsJSON,} from "@excalidraw/excalidraw";
import AwesomeDebouncePromise from 'awesome-debounce-promise';

// hack to access the non typed window object (any) to add old school javscript
var anyWindow = (window as any);

// placeholder for functions
// window.loadBlob = null; // not supported yet
anyWindow.updateApp = null;
anyWindow.updateAppState = null;
anyWindow.saveAsJson = null;
anyWindow.saveAsSvg = null;
anyWindow.saveAsPng = null;

const defaultInitialData = {
    gridMode: false,
    zenMode: false,
    theme: "light",
    debounceAutoSaveInMs: 300
}
const initialData = anyWindow.initialData ?? defaultInitialData;

let currentSceneVersion = getSceneVersion([]); // scene elements are empty on load


window.addEventListener("message", (e) => {
    const message = e.data;
    console.debug("got event: " + message.type + ", message: ", message);
    switch (message.type) {
        case "update": {
            const {elements} = message;
            let updateSceneVersion = getSceneVersion(elements);
            if (currentSceneVersion !== updateSceneVersion) {
                currentSceneVersion = updateSceneVersion;
                anyWindow.updateApp({
                    elements: elements
                });
            }
            break;
        }
        
        case "toggle-read-only": {
            anyWindow.setViewModeEnabled(message.readOnly);
            break;
        }

        case "toggle-scene-modes": {
            let modes = message.sceneModes ?? {};
            if ("gridMode" in modes) anyWindow.setGridModeEnabled(modes.gridMode);
            if ("zenMode" in modes) anyWindow.setZenModeEnabled(modes.zenMode);
            break;
        }

        case "theme-change": {
            anyWindow.setTheme(message.theme);
            break;
        }

        case "save-as-json": {
            dispatchToPlugin({
                type: "json-content",
                json: anyWindow.saveAsJson(),
            });
            break;
        }

        case "save-as-svg": {
            let exportConfig = message.exportConfig ?? {};
            let svg = anyWindow.saveAsSvg(exportConfig);
            dispatchToPlugin({
                type: "svg-content",
                svg: svg.outerHTML,
                correlationId: message.correlationId ?? null
            });
            break;
        }

        case "save-as-png": {
            let exportConfig = message.exportConfig ?? {};
            anyWindow.saveAsPng(exportConfig).then((blob:any) => {
                let reader = new FileReader();
                reader.readAsDataURL(blob);
                reader.onloadend = function () {
                    let base64data = reader.result;
                    dispatchToPlugin({
                        type: "png-base64-content",
                        png: base64data,
                        correlationId: message.correlationId ?? null
                    });
                };
            });
            break;
        }
    }
});


anyWindow.continuousSaving = (elements:object[], appState:object) => {
    console.debug("debounced scene changed")
    // @ts-ignore
    let newSceneVersion = getSceneVersion(elements);
    // maybe check appState
    if (newSceneVersion !== currentSceneVersion) {
        currentSceneVersion = newSceneVersion;

        let jsonContent = anyWindow.saveAsJson();

        dispatchToPlugin({
            type: "continuous-update",
            content: jsonContent,
        });
    }
}

const debouncedContinuousSaving = AwesomeDebouncePromise(
    anyWindow.continuousSaving,
    initialData.debounceAutoSaveInMs
);


export default function App() {
    const excalidrawApiRef = React.useRef(null);
    const excalidrawRef = React.useCallback((excalidrawApi) => {
        excalidrawApiRef.current = excalidrawApi;
        dispatchToPlugin({ type: "ready" })
    }, []);



    const [theme, setTheme] = React.useState(initialData.theme);
    anyWindow.setTheme = setTheme;
    const [viewModeEnabled, setViewModeEnabled] = React.useState(false);
    anyWindow.setViewModeEnabled = setViewModeEnabled;
    const [gridModeEnabled, setGridModeEnabled] = React.useState(initialData.gridMode);
    anyWindow.setGridModeEnabled = setGridModeEnabled;
    const [zenModeEnabled, setZenModeEnabled] = React.useState(initialData.zenMode);
    anyWindow.setZenModeEnabled = setZenModeEnabled;
    // const [exportWithDarkMode, setExportWithDarkMode] = React.useState(false);
    // see https://codesandbox.io/s/excalidraw-forked-xsw0k?file=/src/App.js


    // @ts-ignore
    anyWindow.updateApp = ({elements, appState}) => {
        (excalidrawApiRef.current as any).updateScene({
            elements: elements,
            appState: appState,
        });
    };

    anyWindow.updateAppState = (appState:object) => {
        (excalidrawApiRef.current as any).updateScene({
            elements: (excalidrawApiRef.current as any).getSceneElements(),
            appState: {
                ...(excalidrawApiRef.current as any).getAppState(),
                ...appState
            },
        });
    };

    anyWindow.saveAsJson = () => {
        return serializeAsJSON(
            (excalidrawApiRef.current as any).getSceneElements(),
            (excalidrawApiRef.current as any).getAppState()
        )
    }

    // exportParams
    //
    // exportBackground: boolean (true) Indicates whether background should be exported
    // viewBackgroundColor: string (#fff) The default background color
    // exportWithDarkMode: boolean (false) Indicates whether to export with dark mode

    anyWindow.saveAsSvg = (exportParams:object) => {
        console.debug("saveAsSvg export config", exportParams);
        return exportToSvg({
            elements: (excalidrawApiRef.current as any).getSceneElements(),
            appState: {
                ...(excalidrawApiRef.current as any).getAppState(),
                ...exportParams
            },
        });
    };

    anyWindow.saveAsPng = (exportParams:object) => {
        console.debug("saveAsSvg export config", exportParams);
        return exportToBlob({
            elements: (excalidrawApiRef.current as any).getSceneElements(),
            appState: {
                ...(excalidrawApiRef.current as any).getAppState(),
                ...exportParams
            },
        });
    };

    let onDrawingChange = async (elements:any, state:object) => {
        await debouncedContinuousSaving(elements, state);
    };

    
    return (
        <div className="excalidraw-wrapper">
            <Excalidraw
                ref={excalidrawRef}
                // initialData={InitialData}
                // initialData={{ elements: initialElements, appState: initialAppState, libraryItems: libraryItems }}
                initialData={{
                    appState: {
                        exportEmbedScene: true
                    }
                }}
                onChange={(elements, state) => {
                    console.debug("scene changed")
                    onDrawingChange(elements, state).then(ignored => {
                    })
                }
                }
                onCollabButtonClick={() =>
                    window.alert("Not supported")
                }
                viewModeEnabled={viewModeEnabled}
                zenModeEnabled={zenModeEnabled}
                gridModeEnabled={gridModeEnabled}
                theme={theme}
                // UIOptions={{ canvasActions: { clearCanvas: false, export: false, loadScene: false, saveScene: false } }}
                UIOptions={{canvasActions: {loadScene: false}}}
            />
        </div>
    );
}

function dispatchToPlugin(message:object) {
    console.debug("dispatchToPlugin: ", message);
    // noinspection JSUnresolvedVariable
    if (anyWindow.cefQuery) {
        anyWindow.cefQuery({
            request: JSON.stringify(message),
            persistent: false,
            onSuccess: function (response:any) {
                console.debug("success for message", message, ", response", response);
            },
            onFailure: function (error_code:any, error_message:any) {
                console.debug("failure for message", message, ", error_code", error_code, ", error_message", error_message);
            }
        });
    }
}