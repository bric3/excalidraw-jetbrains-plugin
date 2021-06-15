// import React, { useEffect, useState, useRef } from "react";
// import Excalidraw, {
//     exportToSvg,
//     exportToBlob,
//     getSceneVersion,
// } from "@excalidraw/excalidraw";

// import "./styles.css";

// placeholder for functions
let updateApp = null;
let toSVG = null;
let toPNG = null;

// Used to stop unecessary updates
let readOnly = false
let gridMode = true
let zenMode = false
let theme = "dark" // "light"

const App = () => {
    const excalidrawRef = React.useRef(null);
    const excalidrawWrapperRef = React.useRef(null);

    return React.createElement(
        React.Fragment,
        null,
        React.createElement(
            "div",
            {
                className: "excalidraw-wrapper",
                ref: excalidrawWrapperRef
            },
            React.createElement(Excalidraw.default, {
                ref: excalidrawRef,
                // initialData: InitialData,
                // initialData={{ elements: initialElements, appState: intitialAppState, libraryItems: libraryItems }}
                // UIOptions={{ canvasActions: { clearCanvas: false, export: false, loadScene: false, saveScene: false } }}
                onChange: (elements, state) =>
                    console.log("Elements :", elements, "State : ", state),
                
                onCollabButtonClick: () => window.alert("Not supported"),
                theme: theme,
                viewModeEnabled: readOnly,
                zenModeEnabled: zenMode,
                gridModeEnabled: gridMode
            })
        )
    );
};

const root = document.getElementById("root");  // see index.html
ReactDOM.render(React.createElement(App), root);