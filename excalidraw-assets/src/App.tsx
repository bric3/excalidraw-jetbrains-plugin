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
// Import Excalidraw CSS - required in 0.18.0
import "@excalidraw/excalidraw/index.css";
import type { ExcalidrawImperativeAPI, AppState, BinaryFiles } from "@excalidraw/excalidraw/types";
import type { ExcalidrawElement } from "@excalidraw/excalidraw/element/types";
import AwesomeDebouncePromise from 'awesome-debounce-promise';

// Theme type from Excalidraw
type Theme = "light" | "dark";

// Set EXCALIDRAW_ASSET_PATH so Excalidraw knows where to load fonts from
// This is critical for deprecated fonts like Virgil to be found
// Must be set BEFORE Excalidraw component mounts
(window as Window & { EXCALIDRAW_ASSET_PATH?: string }).EXCALIDRAW_ASSET_PATH = "/";

// Font lineHeight values from Excalidraw's FONT_METADATA
// Used to fix legacy files that don't have lineHeight stored
const FONT_LINE_HEIGHTS: Record<number, number> = {
    1: 1.25,   // Virgil (deprecated)
    2: 1.15,   // Helvetica (deprecated)
    3: 1.2,    // Cascadia (deprecated)
    5: 1.25,   // Excalifont
    6: 1.25,   // Nunito
    7: 1.15,   // Lilita One
    8: 1.25,   // Comic Shanns
};

// Pre-process legacy elements that are missing required fields
// This fixes files created with old Excalidraw versions
function preprocessLegacyElements(elements: unknown[]): unknown[] {
    console.debug("Preprocessing legacy elements:", elements.length);

    return elements.map(el => {
        const element = el as Record<string, unknown>;
        const fixed = { ...element };

        // Fix boundElements: null -> [] (for all element types)
        if (element.boundElements === null) {
            fixed.boundElements = [];
        }

        // Add frameId if missing (for all element types)
        if (!('frameId' in element)) {
            fixed.frameId = null;
        }

        // Arrow-specific fixes: convert old binding format to new format
        if (element.type === 'arrow') {
            // Convert startBinding from old format to new format
            if (element.startBinding && !('mode' in (element.startBinding as Record<string, unknown>))) {
                const oldBinding = element.startBinding as Record<string, unknown>;
                fixed.startBinding = {
                    mode: 'orbit',
                    elementId: oldBinding.elementId,
                    fixedPoint: [0.5, 0.5] // Default center point
                };
            }
            // Convert endBinding from old format to new format
            if (element.endBinding && !('mode' in (element.endBinding as Record<string, unknown>))) {
                const oldBinding = element.endBinding as Record<string, unknown>;
                fixed.endBinding = {
                    mode: 'orbit',
                    elementId: oldBinding.elementId,
                    fixedPoint: [0.5, 0.5] // Default center point
                };
            }
        }

        // Text-specific fixes
        if (element.type === 'text') {
            const fontFamily = (element.fontFamily as number) || 1;
            const lineHeight = FONT_LINE_HEIGHTS[fontFamily] || 1.25;

            // CRITICAL: lineHeight must be present for text to render
            if (element.lineHeight == null) {
                fixed.lineHeight = lineHeight;
            }

            // Add autoResize if missing
            if (element.autoResize == null) {
                fixed.autoResize = true;
            }

            // Height can be 0 or null - Excalidraw recalculates if lineHeight is present
            // But set to 0 if null to avoid issues
            if (element.height === null) {
                fixed.height = 0;
            }
        }

        return fixed;
    });
}

// hack to access the non typed window object (any) to add old school javascript
const anyWindow = window as Window & {
    initialData?: typeof defaultInitialData;
    cefQuery?: (params: {
        request: string;
        persistent: boolean;
        onSuccess: (response: unknown) => void;
        onFailure: (error_code: unknown, error_message: unknown) => void;
    }) => void;
};

const defaultInitialData = {
    readOnly: false,
    gridMode: false,
    zenMode: false,
    theme: "light" as Theme,
    debounceAutoSaveInMs: 300
}
const initialData = anyWindow.initialData ?? defaultInitialData;

class ExcalidrawApiBridge {
    private readonly excalidrawRef: React.MutableRefObject<ExcalidrawImperativeAPI | null>;
    private continuousSavingEnabled = true;
    private _setTheme: React.Dispatch<React.SetStateAction<Theme>> | null = null;
    set setTheme(value: React.Dispatch<React.SetStateAction<Theme>>) {
        this._setTheme = value;
    }

    private _setViewModeEnabled: React.Dispatch<React.SetStateAction<boolean>> | null = null;
    set setViewModeEnabled(value: React.Dispatch<React.SetStateAction<boolean>>) {
        this._setViewModeEnabled = value;
    }

    private _setGridModeEnabled: React.Dispatch<React.SetStateAction<boolean>> | null = null;
    set setGridModeEnabled(value: React.Dispatch<React.SetStateAction<boolean>>) {
        this._setGridModeEnabled = value;
    }

    private _setZenModeEnabled: React.Dispatch<React.SetStateAction<boolean>> | null = null;
    set setZenModeEnabled(value: React.Dispatch<React.SetStateAction<boolean>>) {
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
        return this.excalidrawRef.current!;
    }

    readonly updateApp = ({ elements, appState }: { elements: readonly ExcalidrawElement[]; appState: Partial<AppState> }) => {
        this.excalidraw().updateScene({
            elements: elements,
            appState: appState,
        });
    };

    readonly updateAppState = (appState: Partial<AppState>) => {
        this.excalidraw().updateScene({
            elements: this.excalidraw().getSceneElements(),
            appState: {
                ...this.excalidraw().getAppState(),
                ...appState
            },
        });
    };

    readonly saveAsJson = () => {
        const binaryFiles: BinaryFiles = {};
        return serializeAsJSON(
            this.excalidraw().getSceneElements(),
            this.excalidraw().getAppState(),
            binaryFiles,
            "local"
        )
    };

    readonly saveAsSvg = (exportParams: Partial<AppState>) => {
        console.debug("saveAsSvg export config", exportParams);
        const sceneElements = this.excalidraw().getSceneElements();
        const appState = this.excalidraw().getAppState();

        // Doc: https://docs.excalidraw.com/docs/@excalidraw/excalidraw/api/utils/export#exporttosvg
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

    readonly saveAsBlob = (exportParams: Partial<AppState>, mimeType: string) => {
        console.debug("saveAsPng export config", exportParams);
        const sceneElements = this.excalidraw().getSceneElements();
        const appState = this.excalidraw().getAppState();

        const binaryFiles: BinaryFiles = {};
        // Doc: https://docs.excalidraw.com/docs/@excalidraw/excalidraw/api/utils/export#exporttoblob
        return exportToBlob({
            elements: sceneElements,
            appState: {
                ...appState,
                ...exportParams,
                exportEmbedScene: true,
            },
            files: binaryFiles,
            mimeType: mimeType,
        });
    };

    currentSceneVersion = getSceneVersion([]); // scene elements are empty on load

    private _continuousSaving = (elements: readonly ExcalidrawElement[], _appState: AppState) => {
        if (!this.continuousSavingEnabled) {
            return;
        }
        console.debug("debounced scene changed")
        const newSceneVersion = getSceneVersion(elements);
        // maybe check appState
        if (this.currentSceneVersion !== newSceneVersion) {
            this.currentSceneVersion = newSceneVersion;

            const jsonContent = this.saveAsJson();

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
        if (anyWindow.cefQuery) {
            anyWindow.cefQuery({
                request: JSON.stringify(message),
                persistent: false,
                onSuccess: function (response: unknown) {
                    console.debug("success for message", message, ", response", response);
                },
                onFailure: function (error_code: unknown, error_message: unknown) {
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
                const { elements } = message;
                const bridge = this;

                // Disable continuous saving while updating
                bridge.continuousSavingEnabled = false;

                (async () => {
                    try {
                        // Preprocess elements to fix legacy format issues
                        const processedElements = preprocessLegacyElements(elements);

                        // Create a proper Excalidraw JSON structure and use loadFromBlob
                        // to let Excalidraw handle all format normalization internally
                        const excalidrawData = {
                            type: "excalidraw",
                            version: 2,
                            source: "excalidraw-jetbrains-plugin",
                            elements: processedElements,
                            appState: {},
                            files: {}
                        };

                        const jsonString = JSON.stringify(excalidrawData);
                        const blob = new Blob([jsonString], { type: 'application/json' });
                        const restoredData = await loadFromBlob(blob, null, null);

                        const restoredElements = restoredData.elements || [];
                        const updateSceneVersion = getSceneVersion(restoredElements);

                        if (bridge.currentSceneVersion !== updateSceneVersion) {
                            bridge.currentSceneVersion = updateSceneVersion;
                            bridge.updateApp({
                                elements: restoredElements,
                                appState: restoredData.appState || {}
                            });
                        }
                    } catch (error: unknown) {
                        console.error("Error in update handler:", error);
                        bridge.dispatchToPlugin({
                            type: "excalidraw-error",
                            errorMessage: "cannot load elements"
                        });
                    }
                })();

                break;
            }

            case "load-from-file": {
                const { fileToFetch } = message;
                const bridge = this;

                fetch('/vfs/' + fileToFetch)
                    .then(r => r.text())
                    .then(async (jsonText) => {
                        // Disable continuous saving as the plugin uses IntelliJ's auto-saving mechanism
                        bridge.continuousSavingEnabled = false;

                        try {
                            // Parse the JSON first to preprocess legacy elements
                            const parsedData = JSON.parse(jsonText) as { elements?: unknown[]; appState?: Partial<AppState> };

                            // Preprocess elements BEFORE passing to loadFromBlob
                            if (parsedData.elements) {
                                parsedData.elements = preprocessLegacyElements(parsedData.elements);
                            }

                            // Re-create blob with preprocessed data and use loadFromBlob
                            // to let Excalidraw handle format normalization
                            const preprocessedJson = JSON.stringify(parsedData);
                            const blob = new Blob([preprocessedJson], { type: 'application/json' });
                            const restoredData = await loadFromBlob(blob, null, null);

                            const elements = restoredData.elements || [];
                            const updateSceneVersion = getSceneVersion(elements);

                            if (bridge.currentSceneVersion !== updateSceneVersion) {
                                bridge.currentSceneVersion = updateSceneVersion;
                                bridge.updateApp({
                                    elements: elements,
                                    appState: restoredData.appState || {}
                                });
                            }

                        } catch (error: unknown) {
                            console.error("Error loading file:", error);
                            bridge.dispatchToPlugin({
                                type: "excalidraw-error",
                                errorMessage: "cannot load file"
                            });
                        }
                    })
                    .catch(fetchError => {
                        console.error("Fetch error:", fetchError);
                    });

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
                        const base64data = reader.result;
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
        apiBridge!.dispatchToPlugin({ type: "ready" })

        // Force refresh after fonts load to fix text rendering
        // Fonts load asynchronously in 0.18.0, so we need to refresh once they're ready
        if (document.fonts && document.fonts.ready) {
            document.fonts.ready.then(() => {
                console.debug("Fonts loaded, refreshing Excalidraw");
                excalidrawApi.refresh();
            });
        }
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


    const onDrawingChange = async (elements: readonly ExcalidrawElement[], state: AppState) => {
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
                    console.debug("scene changed");
                    onDrawingChange(elements, state).then(() => {
                        // intentionally empty
                    });
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
                 * See list of available items
                    https://github.com/excalidraw/excalidraw/blob/v0.18.0/packages/excalidraw/components/main-menu/DefaultItems.tsx
                 * Default menu
                    https://github.com/excalidraw/excalidraw/blob/v0.18.0/excalidraw-app/components/AppMainMenu.tsx
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
