import React from "react";
import { createRoot } from "react-dom/client";

import "./styles.css";

import { App } from "./App";

// https://react.dev/blog/2024/04/25/react-19-upgrade-guide
const rootContainer = document.getElementById("root");
const root = createRoot(rootContainer!);
root.render(React.createElement(App));
