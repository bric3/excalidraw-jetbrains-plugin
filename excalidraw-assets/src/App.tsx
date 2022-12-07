import React from "react";
import {
    loadFromBlob,
    exportToBlob,
    exportToSvg,
    getSceneVersion,
    serializeAsJSON,
    Excalidraw,
} from "@excalidraw/excalidraw";
import AwesomeDebouncePromise from 'awesome-debounce-promise';
import {RestoredDataState} from "@excalidraw/excalidraw/types/data/restore";
import {Theme} from "@excalidraw/excalidraw/types/element/types";
import {ExcalidrawImperativeAPI} from "@excalidraw/excalidraw/types/types";

// hack to access the non typed window object (any) to add old school javascript
let anyWindow = (window as any);

const defaultInitialData = {
    readOnly: false,
    gridMode: false,
    zenMode: false,
    theme: "light",
    debounceAutoSaveInMs: 300
}
const initialData = anyWindow.initialData ?? defaultInitialData;

class ExcalidrawApiBridge {
    private readonly excalidrawRef: any;
    private continuousSavingEnabled = true;
    private _setTheme: React.Dispatch<Theme> | null = null;
    set setTheme(value: React.Dispatch<Theme>) {
        this._setTheme = value;
    }

    private _setViewModeEnabled: React.Dispatch<boolean> | null = null;
    set setViewModeEnabled(value: React.Dispatch<boolean>) {
        this._setViewModeEnabled = value;
    }

    private _setGridModeEnabled: React.Dispatch<boolean> | null = null;
    set setGridModeEnabled(value: React.Dispatch<boolean>) {
        this._setGridModeEnabled = value;
    }

    private _setZenModeEnabled: React.Dispatch<boolean> | null = null;
    set setZenModeEnabled(value: React.Dispatch<boolean>) {
        this._setZenModeEnabled = value;
    }

    constructor(excalidrawRef: React.MutableRefObject<ExcalidrawImperativeAPI | null>) {
        this.excalidrawRef = excalidrawRef;
        window.addEventListener(
            "message",
            this.pluginMessageHandler.bind(this)
        );
    }

    private excalidraw() {
        return this.excalidrawRef.current;
    }

    // @ts-ignore
    readonly updateApp = ({elements, appState}) => {
        this.excalidraw().updateScene({
            elements: elements,
            appState: appState,
        });
    };

    readonly updateAppState = (appState: object) => {
        this.excalidraw().updateScene({
            elements: this.excalidraw().getSceneElements(),
            appState: {
                ...this.excalidraw().getAppState(),
                ...appState
            },
        });
    };

    readonly saveAsJson = () => {
        let binaryFiles = {};
        return serializeAsJSON(
            this.excalidraw().getSceneElements(),
            this.excalidraw().getAppState(),
            binaryFiles,
            "local"
        )
    };

    readonly saveAsSvg = (exportParams: object) => {
        console.debug("saveAsSvg export config", exportParams);
        let sceneElements = this.excalidraw().getSceneElements();
        let appState = this.excalidraw().getAppState();

        // const metadata = await encodeSvgMetadata({text: serializeAsJSON(sceneElements, appState)});
        return exportToSvg({
            elements: sceneElements,
            appState: {
                ...appState,
                ...exportParams,
                exportEmbedScene: true,
            },
            files: {},
        });
    };

    readonly saveAsPng = (exportParams: object) => {
        console.debug("saveAsPng export config", exportParams);
        let sceneElements = this.excalidraw().getSceneElements();
        let appState = this.excalidraw().getAppState();

        let binaryFiles = {};
        return exportToBlob({
            elements: sceneElements,
            appState: {
                ...appState,
                ...exportParams,
                exportEmbedScene: true,
            },
            files: binaryFiles,
        });
    };

    currentSceneVersion = getSceneVersion([]); // scene elements are empty on load

    private _continuousSaving = (elements: object[], appState: object) => {
        if (!this.continuousSavingEnabled) {
            return;
        }
        console.debug("debounced scene changed")
        // @ts-ignore
        const newSceneVersion = getSceneVersion(elements);
        // maybe check appState
        if (this.currentSceneVersion !== newSceneVersion) {
            this.currentSceneVersion = newSceneVersion;

            let jsonContent = this.saveAsJson();

            this.dispatchToPlugin({
                type: "continuous-update",
                content: jsonContent,
            });
        }
    }
    debouncedContinuousSaving = AwesomeDebouncePromise(
        this._continuousSaving,
        initialData.debounceAutoSaveInMs
    )

    dispatchToPlugin(message: object): void {
        console.debug("dispatchToPlugin: ", message);

        // cefQuery is only available in the JCEF browser
        // noinspection TypeScriptUnresolvedVariable
        if (anyWindow.cefQuery) {
            // noinspection TypeScriptUnresolvedFunction,JSUnusedGlobalSymbols
            anyWindow.cefQuery({
                request: JSON.stringify(message),
                persistent: false,
                onSuccess: function (response: any) {
                    console.debug("success for message", message, ", response", response);
                },
                onFailure: function (error_code: any, error_message: any) {
                    console.debug("failure for message", message, ", error_code", error_code, ", error_message", error_message);
                }
            });
        }
    }


    private pluginMessageHandler(e: MessageEvent) {
        const message = e.data;
        console.debug("got event: " + message.type + ", message: ", message);
        switch (message.type) {
            case "update": {
                const {elements} = message;
                const updateSceneVersion = getSceneVersion(elements);
                if (this.currentSceneVersion !== updateSceneVersion) {
                    this.currentSceneVersion = updateSceneVersion;
                    this.updateApp({
                        elements: elements || [],
                        appState: {} // TODO load appState ?
                    });
                }
                break;
            }

            case "load-from-file": {
                const {fileToFetch} = message
                fetch('/vfs/' + fileToFetch)
                    .then(response => response.blob())
                    .then(async blob => {
                        // as the plugin uses IntelliJ's auto-saving mechanism instead.
                        this.continuousSavingEnabled = false

                        try {
                            return loadFromBlob(blob, null, null);
                        } catch (error) {
                            console.error(error)
                            this.dispatchToPlugin({
                                type: "excalidraw-error",
                                errorMessage: "cannot load image"
                            })
                        }
                    })
                    .then((restoredState: RestoredDataState | undefined) => {
                        if (!restoredState) {
                            return;
                        }
                        
                        const updateSceneVersion = getSceneVersion(restoredState.elements);
                        if (this.currentSceneVersion !== updateSceneVersion) {
                            this.currentSceneVersion = updateSceneVersion;
                            this.updateApp({
                                elements: restoredState.elements || [],
                                appState: {}  // TODO load appState ? (restoredState.appState)
                            });
                        }
                    })

                break;
            }

            case "toggle-read-only": {
                this._setViewModeEnabled!(message.readOnly);
                break;
            }

            case "toggle-scene-modes": {
                // noinspection TypeScriptUnresolvedVariable
                const modes = message.sceneModes ?? {};
                if ("gridMode" in modes) this._setGridModeEnabled!(modes.gridMode);
                if ("zenMode" in modes) this._setZenModeEnabled!(modes.zenMode);
                break;
            }

            case "theme-change": {
                this._setTheme!(message.theme);
                break;
            }

            case "save-as-json": {
                this.dispatchToPlugin({
                    type: "json-content",
                    json: this.saveAsJson(),
                    correlationId: message.correlationId ?? null
                });
                break;
            }

            case "save-as-svg": {
                // noinspection TypeScriptUnresolvedVariable
                const exportConfig = message.exportConfig ?? {};
                this.saveAsSvg(exportConfig).then(svg => {
                    this.dispatchToPlugin({
                        type: "svg-content",
                        svg: svg.outerHTML,
                        correlationId: message.correlationId ?? null
                    });
                })
                break;
            }

            case "save-as-png": {
                // noinspection TypeScriptUnresolvedVariable
                const exportConfig = message.exportConfig ?? {};
                const thisBridge = this;
                this.saveAsPng(exportConfig).then((blob: Blob) => {
                    const reader = new FileReader();
                    reader.readAsDataURL(blob);
                    reader.onloadend = function () {
                        let base64data = reader.result;
                        thisBridge.dispatchToPlugin({
                            type: "png-base64-content",
                            png: base64data,
                            correlationId: message.correlationId ?? null
                        });
                    };
                });
                break;
            }
        }
    }
}

let apiBridge: ExcalidrawApiBridge | null = null;


export const App = () => {
    const excalidrawApiRef = React.useRef<ExcalidrawImperativeAPI | null>(null);
    apiBridge = new ExcalidrawApiBridge(excalidrawApiRef)

    const excalidrawRef = React.useCallback((excalidrawApi: ExcalidrawImperativeAPI) => {
        excalidrawApiRef.current = excalidrawApi;
        apiBridge!.dispatchToPlugin({type: "ready"})
    }, []);

    // React Hook "React.useState" cannot be called in a class component.
    const [theme, setTheme] = React.useState<Theme>(initialData.theme);
    apiBridge.setTheme = setTheme;
    const [viewModeEnabled, setViewModeEnabled] = React.useState<boolean>(initialData.readOnly);
    apiBridge.setViewModeEnabled = setViewModeEnabled;
    const [gridModeEnabled, setGridModeEnabled] = React.useState<boolean>(initialData.gridMode);
    apiBridge.setGridModeEnabled = setGridModeEnabled;
    const [zenModeEnabled, setZenModeEnabled] = React.useState<boolean>(initialData.zenMode);
    apiBridge.setZenModeEnabled = setZenModeEnabled;
    // see https://codesandbox.io/s/excalidraw-forked-xsw0k?file=/src/App.js


    let onDrawingChange = async (elements: any, state: object) => {
        await apiBridge!.debouncedContinuousSaving(elements, state);
    };


    return (
        <div className="excalidraw-wrapper">
            <Excalidraw
                ref={excalidrawRef}
                // initialData={InitialData}
                // initialData={{ elements: initialElements, appState: initialAppState, libraryItems: libraryItems }}
                initialData={{
                    appState: {
                        // Always embed scene
                        exportEmbedScene: true
                    }
                }}
                onChange={(elements, state) => {
                    console.debug("scene changed")
                    onDrawingChange(elements, state).then(ignored => {
                    })
                }}
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
