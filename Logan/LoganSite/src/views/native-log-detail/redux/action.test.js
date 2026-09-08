import axios from "axios";

jest.mock("axios", () => ({
  create: jest.fn(() => ({
    get: jest.fn(),
    interceptors: {
      request: { use: jest.fn() },
      response: { use: jest.fn() }
    }
  }))
}));
jest.mock("antd", () => ({ message: { warning: jest.fn(), error: jest.fn() } }));

const previousEnv = process.defineEnv;
process.defineEnv = { API_BASE_URL: "http://localhost" };
const { fetchNativeTaskDetailsByDetailIdsApi } = require("../../../common/api");
const { fetchPageInitData, updateFilterConditions, updateSorted } = require("./action");
const reducer = require("./reducer").default;
process.defineEnv = previousEnv;
const client = axios.create.mock.results[0].value;

let state;
let dispatch;
const getState = () => ({ nativeLogDetail: state });

beforeEach(() => {
  client.get.mockReset();
  state = reducer(undefined, {});
  dispatch = action => { state = reducer(state, action); };
});

test.each(["", "   ", null, undefined])("empty detail IDs %p do not send a request", async ids => {
  await expect(fetchNativeTaskDetailsByDetailIdsApi(ids)).resolves.toEqual([]);
  expect(client.get).not.toHaveBeenCalled();
});

test("nonempty IDs still fetch details", async () => {
  const details = [{ id: 1 }];
  client.get.mockResolvedValue(details);
  await expect(fetchNativeTaskDetailsByDetailIdsApi("1,2")).resolves.toEqual(details);
  expect(client.get).toHaveBeenCalledWith("/logan/task/query/details.json", {
    params: { detailIds: "1,2" }
  });
});

test("real request errors still propagate", async () => {
  const error = new Error("network failure");
  client.get.mockRejectedValue(error);
  await expect(fetchNativeTaskDetailsByDetailIdsApi("1")).rejects.toBe(error);
});

test("an empty task initializes its metadata and clears old details", async () => {
  const info = { taskId: 1 };
  client.get.mockImplementation(url => {
    if (url.endsWith("/info.json")) return Promise.resolve(info);
    if (url.endsWith("/logtypes.json")) return Promise.resolve([{ logType: 1 }]);
    if (url.endsWith("/brief.json")) return Promise.resolve([]);
    throw new Error("Unexpected request: " + url);
  });
  state = { ...state, taskDetails: [{ id: 99 }] };
  await fetchPageInitData(1)(dispatch, getState);
  expect(state.logInfo).toEqual(info);
  expect(state.briefs).toEqual([]);
  expect(state.taskDetails).toEqual([]);
  expect(client.get).toHaveBeenCalledTimes(3);
});

test("a filter with no matches clears the previous details", async () => {
  state = { ...state, logInfo: { taskId: 1 }, taskDetails: [{ id: 99 }] };
  client.get.mockResolvedValue([]);
  await updateFilterConditions({ logTypes: [], keyword: "no matches" })(dispatch, getState);
  expect(state.briefs).toEqual([]);
  expect(state.taskDetails).toEqual([]);
  expect(client.get).toHaveBeenCalledTimes(1);
});

test.each([true, false])("sorting an empty task (%p) does not crash or request empty IDs", async sorted => {
  state = { ...state, logInfo: { taskId: 1 }, taskDetails: [{ id: 99 }] };
  client.get.mockResolvedValue([]);
  await updateSorted(sorted)(dispatch, getState);
  expect(state.briefs).toEqual([]);
  expect(state.taskDetails).toEqual([]);
  expect(state.focusLogId).toBe(-1);
  expect(client.get).toHaveBeenCalledTimes(1);
});
