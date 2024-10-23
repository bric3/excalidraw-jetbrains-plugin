import {createContext, useContext} from "react";
import {ExcalidrawApiBridge} from "./ExcalidrawApiBridge";

export interface BridgeContextType {
    bridge: ExcalidrawApiBridge
}

export const BridgeContext = createContext<BridgeContextType | null>(null);

export function useBridge() {
    const context = useContext(BridgeContext)
    if (!context) {
        throw new Error("useBridge should be used on a BridgeContext provider");

    }
    return context;
}