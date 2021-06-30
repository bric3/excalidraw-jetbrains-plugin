import React from "react";
import Excalidraw, {exportToBlob, exportToSvg, getSceneVersion, serializeAsJSON,} from "@excalidraw/excalidraw";
import AwesomeDebouncePromise from 'awesome-debounce-promise';

// placeholder for functions
// window.loadBlob = null; // not supported yet
window.updateApp = null;
window.updateAppState = null;
window.saveAsJson = null;
window.saveAsSvg = null;
window.saveAsPng = null;

const defaultInitialData = {
    gridMode: false,
    zenMode: false,
    theme: "light",
    debounceAutoSaveInMs: 300
}
const initialData = window.initialData ? window.initialData : defaultInitialData;

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
                updateApp({
                    elements: elements
                });
            }
            break;
        }
        
        case "toggle-read-only": {
            window.setViewModeEnabled(message.readOnly);
            break;
        }

        case "theme-change": {
            window.setTheme(message.theme);
            break;
        }

        case "save-as-json": {
            dispatchToPlugin({
                type: "json-content",
                json: saveAsJson(),
            });
            break;
        }

        case "save-as-svg": {
            let exportConfig = message.exportConfig ?? {};
            let svg = saveAsSvg(exportConfig);
            dispatchToPlugin({
                type: "svg-content",
                svg: svg.outerHTML,
                correlationId: message.correlationId ?? null
            });
            break;
        }

        case "save-as-png": {
            let exportConfig = message.exportConfig ?? {};
            saveAsPng(exportConfig).then((blob) => {
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


window.continuousSaving = (elements, appState) => {
    console.debug("debounced scene changed")
    let newSceneVersion = getSceneVersion(elements);
    // maybe check appState
    if (newSceneVersion != currentSceneVersion) {
        currentSceneVersion = newSceneVersion;

        let jsonContent = saveAsJson();

        dispatchToPlugin({
            type: "continuous-update",
            content: jsonContent,
        });
    }
}

const debouncedContinuousSaving = AwesomeDebouncePromise(
    continuousSaving,
    initialData.debounceAutoSaveInMs
);


const resolvablePromise = () => {
    let resolve;
    let reject;
    const promise = new Promise((_resolve, _reject) => {
        resolve = _resolve;
        reject = _reject;
    });
    promise.resolve = resolve;
    promise.reject = reject;
    return promise;
};


export default function App() {
    const excalidrawRef = React.useMemo(
        () => ({
            current: {
                readyPromise: resolvablePromise()
            }
        }),
        []
    );

    React.useEffect(() => {
        excalidrawRef.current.readyPromise.then((api) => {
            dispatchToPlugin({type: "ready"});
        });
    }, [excalidrawRef]);


    const [theme, setTheme] = React.useState(initialData.theme);
    window.setTheme = setTheme;
    const [viewModeEnabled, setViewModeEnabled] = React.useState(false);
    window.setViewModeEnabled = setViewModeEnabled;
    const [gridModeEnabled, setGridModeEnabled] = React.useState(initialData.gridMode);
    window.setGridModeEnabled = setGridModeEnabled;
    const [zenModeEnabled, setZenModeEnabled] = React.useState(initialData.zenMode);
    window.setZenModeEnabled = setZenModeEnabled;
    // const [exportWithDarkMode, setExportWithDarkMode] = React.useState(false);
    // see https://codesandbox.io/s/excalidraw-forked-xsw0k?file=/src/App.js


    window.updateApp = ({elements, appState}) => {
        excalidrawRef.current.updateScene({
            elements: elements,
            appState: appState,
        });
    };

    window.updateAppState = (appState) => {
        excalidrawRef.current.updateScene({
            elements: excalidrawRef.current.getSceneElements(),
            appState: {
                ...excalidrawRef.current.getAppState(),
                ...appState
            },
        });
    };

    window.saveAsJson = () => {
        return serializeAsJSON(
            excalidrawRef.current.getSceneElements(),
            excalidrawRef.current.getAppState()
        )
    }

    // exportParams
    //
    // exportBackground: boolean (true) Indicates whether background should be exported
    // viewBackgroundColor: string (#fff) The default background color
    // exportWithDarkMode: boolean (false) Indicates whether to export with dark mode

    window.saveAsSvg = (exportParams) => {
        console.debug("saveAsSvg export config", exportParams);
        return exportToSvg({
            elements: excalidrawRef.current.getSceneElements(),
            appState: {
                ...excalidrawRef.current.getAppState(),
                ...exportParams
            },
        });
    };

    window.saveAsPng = (exportParams) => {
        console.debug("saveAsSvg export config", exportParams);
        return exportToBlob({
            elements: excalidrawRef.current.getSceneElements(),
            appState: {
                ...excalidrawRef.current.getAppState(),
                ...exportParams
            },
        });
    };

    let onDrawingChange = async (elements, state) => {
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
                readyPromise={() => {
                    console.debug("excalidraw ready")
                }}
                viewModeEnabled={viewModeEnabled}
                zenModeEnabled={zenModeEnabled}
                gridModeEnabled={gridModeEnabled}
                exportEmbedScene={true}
                theme={theme}
                // UIOptions={{ canvasActions: { clearCanvas: false, export: false, loadScene: false, saveScene: false } }}
                UIOptions={{canvasActions: {loadScene: false}}}
            />
        </div>
    );
}

function dispatchToPlugin(message) {
    console.debug("dispatchToPlugin: ", message);
    // noinspection JSUnresolvedVariable
    if (window.cefQuery) {
        window.cefQuery({
            request: JSON.stringify(message),
            persistent: false,
            onSuccess: function (response) {
                console.debug("success for message", message, ", response", response);
            },
            onFailure: function (error_code, error_message) {
                console.debug("failure for message", message, ", error_code", error_code, ", error_message", error_message);
            }
        });
    }
}