import React from "react";
import ReactDOM from "react-dom";
import {act} from "react-dom/test-utils";
import {Provider} from "react-redux";
import {createStore, combineReducers, applyMiddleware} from "redux";
import thunk from "redux-thunk";
import {Router, Route, Switch} from "react-router-dom";
import {createMemoryHistory} from "history";
import NativeList from "./native-list";
import WebList from "./web-list";
import nativeReducer from "./native-list/redux/reducer";
import webReducer from "./web-list/redux/reducer";
import {fetchPage, updateFilterConditions} from "./native-list/redux/action";
import LogDetailPage from "./components/log-detail-page";
import {fetchNativeTaskPageApi, fetchWebListInitData} from "../common/api";

jest.mock("./components/list-page/components/header-bar/index", () => () => null);
jest.mock("./components/log-detail-page/components/time-minimap/index", () => () => null);
jest.mock("../common/api", () => ({
  fetchNativeTaskPageApi: jest.fn(), fetchWebListInitData: jest.fn(), fetchWebTaskApi: jest.fn()
}));

let container;
let store;
let history;
const tasks = Array.from({length: 200}, (_, i) => ({
  taskId: i + 1, tasks: String(i + 1), deviceId: "device", platform: 3, logDate: 0, addTime: 0
}));
const flush = async () => { await act(async () => { await Promise.resolve(); }); };
const click = element => act(() => { element.dispatchEvent(new MouseEvent("click", {bubbles: true})); });

function ReturnButton(routeProps) {
  const type = routeProps.location.pathname === "/native-log-detail" ? "native" : "web";
  const state = store.getState();
  const page = new LogDetailPage({
    ...routeProps, type,
    listView: state[type === "native" ? "nativeList" : "webList"].listView,
    nativeListFilterConditions: state.nativeList.appliedFilters,
    webListFilterConditions: state.webList.filterConditions
  });
  return <button id="return-button" onClick={page.handleBackToListButtonClicked}>返回列表</button>;
}

beforeEach(() => {
  fetchNativeTaskPageApi.mockReset().mockImplementation(({page, pageSize}) => Promise.resolve({
    items: tasks.slice((page - 1) * pageSize, page * pageSize), total: tasks.length, page, pageSize
  }));
  fetchWebListInitData.mockReset().mockResolvedValue(tasks);
  container = document.createElement("div");
  document.body.appendChild(container);
  store = createStore(combineReducers({nativeList: nativeReducer, webList: webReducer}), applyMiddleware(thunk));
});

afterEach(() => {
  act(() => { ReactDOM.unmountComponentAtNode(container); });
  container.remove();
});

async function openList(type) {
  history = createMemoryHistory({initialEntries: [type === "native" ? "/native-list" : "/web-list"]});
  act(() => {
    ReactDOM.render(<Provider store={store}><Router history={history}><Switch>
      <Route path="/native-list" component={NativeList}/>
      <Route path="/web-list" component={WebList}/>
      <Route component={ReturnButton}/>
    </Switch></Router></Provider>, container);
  });
  await flush();
  if (type === "native") {
    await act(async () => { await store.dispatch(fetchPage(1, 50)); await store.dispatch(fetchPage(3, 50)); });
  } else {
    click(container.querySelector('.ant-pagination-item[title="3"]'));
  }
}

function openDetail() {
  const body = container.querySelector(".ant-table-body");
  body.scrollTop = 360;
  body.scrollLeft = 45;
  container.querySelector(".table-container").scrollTop = 40;
  click(container.querySelectorAll(".ant-table-row button")[10]);
}

test.each([["native", "button"], ["native", "browser"], ["web", "button"], ["web", "browser"]])(
  "%s list preserves page, rows and scroll after returning via %s", async (type, method) => {
    await openList(type);
    const before = container.querySelector(".ant-table-row").getAttribute("data-row-key");
    const requestCount = fetchNativeTaskPageApi.mock.calls.length + fetchWebListInitData.mock.calls.length;
    openDetail();
    if (method === "button") click(container.querySelector("#return-button"));
    else act(() => { history.goBack(); });
    await flush();
    expect(container.querySelector(".ant-pagination-item-active").textContent).toBe("3");
    expect(container.querySelector(".ant-table-row").getAttribute("data-row-key")).toBe(before);
    expect(container.querySelector(".ant-table-body").scrollTop).toBe(360);
    expect(container.querySelector(".ant-table-body").scrollLeft).toBe(45);
    expect(container.querySelector(".table-container").scrollTop).toBe(40);
    expect(fetchNativeTaskPageApi.mock.calls.length + fetchWebListInitData.mock.calls.length).toBe(requestCount);
    if (type === "native") expect(store.getState().nativeList.pagination.pageSize).toBe(50);
  }
);

test("returning preserves unsubmitted filter edits as well as the displayed page", async () => {
  await openList("native");
  const filters = {...store.getState().nativeList.filterConditions, unionId: "not submitted"};
  act(() => { store.dispatch(updateFilterConditions(filters)); });
  openDetail();
  click(container.querySelector("#return-button"));
  await flush();
  expect(store.getState().nativeList.filterConditions).toEqual(filters);
  expect(store.getState().nativeList.appliedFilters).toEqual({});
});

test("opening a different search does not restore the previous list", async () => {
  await openList("native");
  openDetail();
  act(() => { history.push("/native-list?unionId=new-search"); });
  await flush();
  expect(fetchNativeTaskPageApi).toHaveBeenLastCalledWith({unionId: "new-search", page: 1, pageSize: 50});
  expect(container.querySelector(".ant-pagination-item-active").textContent).toBe("1");
  expect(container.querySelector(".ant-table-body").scrollTop).toBe(0);
});

test("repeated detail visits restore the latest position each time", async () => {
  await openList("native");
  for (let visit = 0; visit < 2; visit++) {
    openDetail();
    click(container.querySelector("#return-button"));
    await flush();
    expect(store.getState().nativeList.pagination.current).toBe(3);
    expect(container.querySelector(".ant-table-body").scrollTop).toBe(360);
  }
});

test("a late page response cannot move the saved list while details are open", async () => {
  await openList("native");
  let finish;
  fetchNativeTaskPageApi.mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
  let pending;
  act(() => { pending = store.dispatch(fetchPage(4, 50)); });
  openDetail();
  await act(async () => {
    finish({items: tasks.slice(150), page: 4, pageSize: 50, total: 200});
    await pending;
  });
  click(container.querySelector("#return-button"));
  await flush();
  expect(store.getState().nativeList.pagination.current).toBe(3);
  expect(container.querySelector(".ant-table-row").getAttribute("data-row-key")).toBe("101");
});

test("an unrelated detail link uses the normal list fallback", () => {
  const push = jest.fn();
  new LogDetailPage({
    type: "native", history: {push}, location: {search: "?tasks=999"},
    listView: {tasks: "111", url: "/native-list?unionId=old"},
    nativeListFilterConditions: {platform: 3}
  }).handleBackToListButtonClicked();
  expect(push).toHaveBeenCalledWith("/native-list?platform=3");
});
