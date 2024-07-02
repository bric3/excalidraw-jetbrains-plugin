import React from "react";
import {
    Excalidraw,
    exportToBlob,
    exportToSvg,
    getSceneVersion,
    loadFromBlob,
    MainMenu,
    serializeAsJSON,
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
    private readonly excalidrawApiRef: React.Ref<ExcalidrawImperativeAPI | null>;
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
        this.excalidrawApiRef = excalidrawRef;
        window.addEventListener(
            "message",
            this.pluginMessageHandler.bind(this)
        );
    }

    private excalidrawApi() : ExcalidrawImperativeAPI {
        // @ts-ignore // Object is initialized in constructor
        return this.excalidrawApiRef.current;
    }

    // @ts-ignore
    readonly updateApp = ({elements, appState}) => {
        this.excalidrawApi().updateScene({
            elements: elements,
            appState: appState,
        });
    };

    readonly updateAppState = (appState: object) => {
        this.excalidrawApi().updateScene({
            elements: this.excalidrawApi().getSceneElements(),
            appState: {
                ...this.excalidrawApi().getAppState(),
                ...appState
            },
        });
    };

    readonly saveAsJson = () => {
        return serializeAsJSON(
            this.excalidrawApi().getSceneElements(),
            this.excalidrawApi().getAppState(),
                this.excalidrawApi().getFiles(),
            "local"
        )
    };

    readonly saveAsSvg = (exportParams: object) => {
        console.debug("saveAsSvg export config", exportParams);
        let sceneElements = this.excalidrawApi().getSceneElements();
        let appState = this.excalidrawApi().getAppState();
        let files = this.excalidrawApi().getFiles();

        // Doc: https://docs.excalidraw.com/docs/@excalidraw/excalidraw/api/utils/export#exporttosvg
        return exportToSvg({
            elements: sceneElements,
            appState: {
                ...appState,
                ...exportParams,
                exportEmbedScene: true,
            },
            files: files,
        });
    };

    readonly saveAsBlob = (exportParams: object, mimeType: string) => {
        console.debug("saveAsPng export config", exportParams);
        let sceneElements = this.excalidrawApi().getSceneElements();
        let appState = this.excalidrawApi().getAppState();
        let files = this.excalidrawApi().getFiles();

        // Doc: https://docs.excalidraw.com/docs/@excalidraw/excalidraw/api/utils/export#exporttoblob
        return exportToBlob({
            elements: sceneElements,
            appState: {
                ...appState,
                ...exportParams,
                exportEmbedScene: true,
            },
            files: files,
            mimeType: mimeType,
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

        // `cefQuery` is only declared and available in the JCEF browser, which explains why it's not resolved here.
        // noinspection JSUnresolvedReference
        if (anyWindow.cefQuery) {
            // noinspection JSUnresolvedReference
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
                        appState: {}, // TODO load appState ?
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
                        } catch (error: unknown) {
                            // Javascript/Typescript errors can be of any type really, even null.
                            let errorStr = error instanceof Error ? error.toString() : JSON.stringify(error);
                            console.error(errorStr)
                            // Also, maybe error can be passed to the dispatcher?
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
                                appState: {},  // TODO load appState ? (restoredState.appState)
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

            case "save-as-binary-image": {
                const exportConfig = message.exportConfig ?? {};
                const mimeType = message.mimeType ?? "image/png";
                const thisBridge = this;
                this.saveAsBlob(exportConfig, mimeType).then((blob: Blob) => {
                    const reader = new FileReader();
                    reader.readAsDataURL(blob);
                    reader.onloadend = function () {
                        let base64data = reader.result;
                        thisBridge.dispatchToPlugin({
                            type: "binary-image-base64-content",
                            base64Payload: base64data,
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
                excalidrawAPI={excalidrawRef}
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
                viewModeEnabled={viewModeEnabled}
                zenModeEnabled={zenModeEnabled}
                gridModeEnabled={gridModeEnabled}
                theme={theme}
                // UIOptions={{ canvasActions: { clearCanvas: false, export: false, loadScene: false, saveScene: false } }}
                UIOptions={{
                    canvasActions: {
                        loadScene: false,
                        saveAsImage: false,
                        saveToActiveFile: false,
                    }
                }}
            >
                { /*
                Customize main menu.
                 * See list ogf available items
                    https://github.com/excalidraw/excalidraw/blob/v0.17.0/src/components/main-menu/DefaultItems.tsx
                 * Default menu
                    https://github.com/excalidraw/excalidraw/blob/v0.17.0/excalidraw-app/components/AppMainMenu.tsx
                */}
                <MainMenu>
                    <MainMenu.DefaultItems.Help />
                    <MainMenu.DefaultItems.ClearCanvas />
                    <MainMenu.Separator />
                    <MainMenu.DefaultItems.ChangeCanvasBackground />
                </MainMenu>
            </Excalidraw>
        </div>
    );
}
