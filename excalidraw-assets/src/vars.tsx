declare global {
    interface Window {
        cefQuery: any
        initialProps: InitialProps
    }
}

type InitialProps = {
    "theme": "light" | "dark",
    "readOnly": false,
    "gridMode": false,
    "zenMode": false,
    "debounceAutoSaveInMs": number
}

export const defaultInitialProps: InitialProps = {
    readOnly: false,
    gridMode: false,
    zenMode: false,
    theme: "light",
    debounceAutoSaveInMs: 300
}

export const initialProps = window.initialProps ?? defaultInitialProps;