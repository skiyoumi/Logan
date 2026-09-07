import {
  fetchNativeLogTypesApi,
  fetchNativeTaskBriefsByTaskIdApi,
  fetchNativeTaskDetailsByDetailIdsApi,
  fetchNativeTaskInfoByTaskIdApi
} from "../../../common/api";
import {fetchPageInitData} from "./action";

jest.mock("../../../common/api", () => ({
  fetchNativeLogTypesApi: jest.fn(),
  fetchNativeTaskBriefsByTaskIdApi: jest.fn(),
  fetchNativeTaskDetailsByDetailIdsApi: jest.fn(),
  fetchNativeTaskInfoByTaskIdApi: jest.fn()
}));

describe("native log detail initialization", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("does not request details when the task has no log briefs", async () => {
    fetchNativeTaskInfoByTaskIdApi.mockResolvedValue({taskId: 7});
    fetchNativeLogTypesApi.mockResolvedValue([]);
    fetchNativeTaskBriefsByTaskIdApi.mockResolvedValue([]);
    fetchNativeTaskDetailsByDetailIdsApi.mockRejectedValue({
      code: 400,
      msg: "param illegal"
    });

    const dispatch = jest.fn();
    const getState = () => ({
      nativeLogDetail: {
        filterConditions: {
          logTypes: [],
          keyword: ""
        }
      }
    });

    await expect(fetchPageInitData(7)(dispatch, getState)).resolves.toBeUndefined();
    expect(fetchNativeTaskDetailsByDetailIdsApi).not.toHaveBeenCalled();
  });
});
