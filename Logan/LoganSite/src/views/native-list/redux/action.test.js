import { fetchNativeTaskPageApi } from "../../../common/api";
import { fetchInitData, fetchTasks, fetchPage } from "./action";
import reducer from "./reducer";

jest.mock("../../../common/api", () => ({ fetchNativeTaskPageApi: jest.fn() }));
let state;
const getState = () => ({ nativeList: state });
const dispatch = action => { state = reducer(state, action); };
const result = (page = 1, pageSize = 20) => ({ items: [{ taskId: page }], total: 65, page, pageSize });

beforeEach(() => { state = reducer(undefined, {}); fetchNativeTaskPageApi.mockReset(); });

test("initial page gets a total beyond the former latest-20 limit", async () => {
  fetchNativeTaskPageApi.mockResolvedValue(result());
  await fetchInitData()(dispatch, getState);
  expect(fetchNativeTaskPageApi).toHaveBeenCalledWith({ page: 1, pageSize: 20 });
  expect(state.pagination).toEqual({ current: 1, pageSize: 20, total: 65 });
  expect(state.loading).toBe(false);
});

test("paging keeps applied search conditions rather than unsubmitted edits", async () => {
  state = { ...state, appliedFilters: { deviceId: "searched", appId: "app", appVersion: "2.1.6", unionId: "用户+甲&乙", platform: 3 }, filterConditions: { deviceId: "draft" } };
  fetchNativeTaskPageApi.mockResolvedValue(result(2));
  await fetchPage(2, 20)(dispatch, getState);
  expect(fetchNativeTaskPageApi).toHaveBeenCalledWith({ deviceId: "searched", appId: "app", appVersion: "2.1.6", unionId: "用户+甲&乙", platform: 3, page: 2, pageSize: 20 });
});

test("new searches and page-size changes start at page one", async () => {
  state = { ...state, pagination: { current: 3, pageSize: 50, total: 200 } };
  fetchNativeTaskPageApi.mockResolvedValue(result(1, 50));
  await fetchTasks({ deviceId: "new", platform: 2 })(dispatch, getState);
  expect(fetchNativeTaskPageApi).toHaveBeenLastCalledWith({ deviceId: "new", platform: 2, page: 1, pageSize: 50 });
  fetchNativeTaskPageApi.mockResolvedValue(result(1, 10));
  await fetchPage(3, 10)(dispatch, getState);
  expect(fetchNativeTaskPageApi).toHaveBeenLastCalledWith({ deviceId: "new", platform: 2, page: 1, pageSize: 10 });
});

test("a slow previous page cannot replace a newer result", async () => {
  let resolveOld;
  fetchNativeTaskPageApi.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve; }));
  const old = fetchPage(2, 20)(dispatch, getState);
  fetchNativeTaskPageApi.mockResolvedValue(result(3));
  await fetchPage(3, 20)(dispatch, getState);
  resolveOld(result(2)); await old;
  expect(state.pagination.current).toBe(3);
  expect(state.tasks).toEqual([{ taskId: 3 }]);
});

test("failure ends loading and preserves the previous page", async () => {
  state = { ...state, tasks: [{ taskId: 9 }] };
  const failure = new Error("failed");
  fetchNativeTaskPageApi.mockRejectedValue(failure);
  await expect(fetchPage(2, 20)(dispatch, getState)).rejects.toBe(failure);
  expect(state.loading).toBe(false);
  expect(state.tasks).toEqual([{ taskId: 9 }]);
});
