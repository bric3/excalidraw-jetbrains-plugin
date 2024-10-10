import {useCallback} from "react";
import {observer} from "mobx-react";
import {Excalidraw, MainMenu,} from "@excalidraw/excalidraw";
import {ExcalidrawElement} from "@excalidraw/excalidraw/types/element/types";
import {AppState, BinaryFiles, ExcalidrawImperativeAPI} from "@excalidraw/excalidraw/types/types";
import {useBridge} from "./bridge-context";

export const App = observer(() => {
    const {bridge} = useBridge();

    const excalidrawRef = useCallback((excalidrawApi: ExcalidrawImperativeAPI) => {
        bridge.setApi(excalidrawApi);
    }, []);

    let onDrawingChange = async (elements: any, state: object, files: BinaryFiles) => {
        bridge!.debouncedContinuousSaving(elements, state, files);
    };

    if (!bridge.isDataReady) {
        return <div>Loading</div>
    }

    return (
            <div className="excalidraw-wrapper z-10">
                <Excalidraw
                        excalidrawAPI={excalidrawRef}
                        initialData={bridge.initialData}
                        onChange={(elements: readonly ExcalidrawElement[], appState: AppState, files: BinaryFiles) => {
                            console.debug("scene changed")
                            onDrawingChange(elements, appState, files);
                        }}
                        viewModeEnabled={bridge.excalidrawOptions.viewMode}
                        zenModeEnabled={bridge.excalidrawOptions.zenMode}
                        gridModeEnabled={bridge.excalidrawOptions.gridMode}
                        theme={bridge.excalidrawOptions.theme}
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
                        https://github.com/excalidraw/excalidraw/blob/v0.17.0/src/components/main-menu/DefaultItems.tsx
                     * Default menu
                        https://github.com/excalidraw/excalidraw/blob/v0.17.0/excalidraw-app/components/AppMainMenu.tsx
                    */}
                    <MainMenu>
                        <MainMenu.DefaultItems.Help/>
                        <MainMenu.DefaultItems.ClearCanvas/>
                        <MainMenu.Separator/>
                        <MainMenu.DefaultItems.ChangeCanvasBackground/>
                    </MainMenu>
                </Excalidraw>
            </div>
    );
})