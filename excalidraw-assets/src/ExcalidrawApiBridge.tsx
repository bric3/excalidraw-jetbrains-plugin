import {
    AppState,
    BinaryFiles,
    ExcalidrawImperativeAPI,
    ExcalidrawInitialDataState,
} from "@excalidraw/excalidraw/types/types";
import {exportToBlob, exportToSvg, getSceneVersion, loadFromBlob, serializeAsJSON} from "@excalidraw/excalidraw";
import AwesomeDebouncePromise from "awesome-debounce-promise";
import {RestoredDataState} from "@excalidraw/excalidraw/types/data/restore";
import {makeAutoObservable, makeObservable, observable, runInAction} from "mobx";
import {initialProps} from "./vars";

export class BridgeDefaultSettings {
    continuousSavingEnabled = true
    debounceAutoSaveInMs = 1000;
}

export class ExcalidrawOptions {
    theme = initialProps.theme
    viewMode: boolean = initialProps.readOnly
    gridMode: boolean = initialProps.gridMode
    zenMode: boolean = initialProps.zenMode

    constructor() {
        makeAutoObservable(this);
    }
}

export class ExcalidrawApiBridge {
    public settings: BridgeDefaultSettings;
    public currentSceneVersion: number;

    debouncedContinuousSaving: (elements: object[], appState: object, files: BinaryFiles) => void;

    excalidrawOptions: ExcalidrawOptions;
    private _excalidrawApi: ExcalidrawImperativeAPI | null = null;

    isApiReady: boolean = false;
    isDataReady: boolean = false;
    isBridgeReady: boolean = false;

    initialData: ExcalidrawInitialDataState | Promise<ExcalidrawInitialDataState | null> | null | undefined;

    constructor() {
        window.addEventListener(
                "message",
                this.pluginMessageHandler.bind(this)
        );

        this.settings = new BridgeDefaultSettings();
        this.currentSceneVersion = getSceneVersion([]); // scene elements are empty on load
        this.excalidrawOptions = new ExcalidrawOptions()

        this.debouncedContinuousSaving = AwesomeDebouncePromise(
                this._continuousSaving,
                this.settings.debounceAutoSaveInMs
        )

        makeObservable(this, {
            isApiReady: observable,
            isDataReady: observable
        })

        this.dispatchToPlugin({type: "ready"})

        runInAction(() => {
            this.isBridgeReady = true;
        })
    }

    get api() {
        if (!this._excalidrawApi) {
            throw new Error("Excalidraw api not defined.")
        }

        return this._excalidrawApi;
    }

    set api(api: ExcalidrawImperativeAPI) {
        if (api) {
            this._excalidrawApi = api;
            runInAction(() => {
                this.isApiReady = true;
            })
        }
    }

    readonly updateApp = (args: Pick<ExcalidrawInitialDataState, "elements"> & { appState?: AppState }) => {
        const {elements, appState} = args;
        this.api.updateScene({elements, appState});
    };

    readonly updateAppState = (appState: object) => {
        this.api.updateScene({
            elements: this.api.getSceneElements(),
            appState: {
                ...this.api.getAppState(),
                ...appState
            },
        });
    };

    readonly saveAsJson = () => {
        const serialized = serializeAsJSON(
                this.api.getSceneElements(),
                this.api.getAppState(),
                this.api.getFiles(),
                "local");

        console.log("saveAsJson", serialized);
        return serialized
    };

    readonly saveAsSvg = (exportParams: object) => {
        console.debug("saveAsSvg export config", exportParams);
        let sceneElements = this.api.getSceneElements();
        let appState = this.api.getAppState();
        let files = this.api.getFiles();

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
        let sceneElements = this.api.getSceneElements();
        let appState = this.api.getAppState();
        let files = this.api.getFiles();

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

    private _continuousSaving = (elements: object[], appState: object, files: BinaryFiles) => {
        if (!this.settings.continuousSavingEnabled) {
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


    dispatchToPlugin(message: object): void {
        console.debug("dispatchToPlugin: ", message);

        window.cefQuery({
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

    private pluginMessageHandler(e: MessageEvent) {
        const message = e.data;
        console.debug("got event: " + message.type + ", message: ", message);
        switch (message.type) {
            case "update": {
                // Receive json from plugin
                console.log("event = update", message)

                const {elements} = message;
                const updateSceneVersion = getSceneVersion(elements);

                if (this.currentSceneVersion !== updateSceneVersion) {
                    this.currentSceneVersion = updateSceneVersion;
                    // this.updateApp({
                    //     elements: elements || [],
                    //     appState: {}, // TODO load appState ?
                    // });
                }

                this.initialData = {
                    type: "excalidraw",
                    version: 2,
                    appState: {
                        gridSize: null,
                        viewBackgroundColor: "#ffffff"
                    },
                    elements: message.elements,
                    files: message.files
                }

                this.isDataReady = true;
                break;
            }

            case "load-from-file": {
                const {fileToFetch} = message
                fetch('/vfs/' + fileToFetch)
                        .then(response => response.blob())
                        .then(async blob => {
                            // as the plugin uses IntelliJ's auto-saving mechanism instead.
                            this.settings.continuousSavingEnabled = false

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

                                });
                                // appState: {},  // TODO load appState ? (restoredState.appState)
                            }
                        })

                break;
            }

            case "toggle-read-only": {
                this.excalidrawOptions.viewMode = message.readOnly;
                break;
            }

            case "toggle-scene-modes": {
                const modes = message.sceneModes ?? {};
                if ("gridMode" in modes) this.excalidrawOptions.gridMode = modes.gridMode;
                if ("zenMode" in modes) this.excalidrawOptions.zenMode = modes.zenMode;
                break;
            }

            case "theme-change": {
                this.excalidrawOptions.theme = message.theme;
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

    setApi(excalidrawApi: ExcalidrawImperativeAPI) {
        this.api = excalidrawApi
    }
}