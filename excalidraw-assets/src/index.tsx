import React from "react";
import { createRoot } from "react-dom/client"; // From React 18

// import "./styles.scss";
import "./styles.css";

import {App} from "./App";
import {BridgeContext} from "./bridge-context";
import {ExcalidrawApiBridge} from "./ExcalidrawApiBridge";
import {DebugHelperView} from "./DebugHelperView";

// https://reactjs.org/blog/2022/03/08/react-18-upgrade-guide.html#updates-to-client-rendering-apis
const rootContainer = document.getElementById("root");
const root = createRoot(rootContainer!)

const bridge = new ExcalidrawApiBridge();

root.render(
        <BridgeContext.Provider value={{bridge}}>
            {/*<DebugHelperView/>*/}
            <App/>
        </BridgeContext.Provider>
);