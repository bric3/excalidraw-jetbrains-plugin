import {observer} from "mobx-react";
import {FC} from "react";
import {useBridge} from "./bridge-context";
import {toJS} from "mobx";

export const DebugHelperView: FC = observer(() => {
    const {bridge} = useBridge()

    return (<div className="flex flex-row bg-gray-50 gap-2 fixed z-20 rounded-sm m-1 p-1">
        <div>{bridge.excalidrawOptions.theme}</div>
        <div>isDataReady: {JSON.stringify(bridge.isDataReady)}</div>
        <div>isApiReady: {JSON.stringify(toJS(bridge.isApiReady))}</div>
        <div>isBridgeReady: {JSON.stringify(toJS(bridge.isBridgeReady))}</div>
    </div>)
});