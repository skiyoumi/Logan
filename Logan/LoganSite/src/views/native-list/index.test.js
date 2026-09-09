import { NativeList } from "./index";
jest.mock("./redux/action", () => ({}));

test.each(["appId=app", "appVersion=2.1.6", "unionId=%E7%94%A8%E6%88%B7%2B%E7%94%B2%26%E4%B9%99", "platform=0"])(
  "restores optional search URL %s without requiring device or dates", query => {
    const props = { location: { search: "?" + query }, updateFilterConditions: jest.fn(), fetchTasks: jest.fn(), fetchInitData: jest.fn() };
    new NativeList(props).componentDidMount();
    expect(props.fetchTasks).toHaveBeenCalledTimes(1);
    expect(props.fetchInitData).not.toHaveBeenCalled();
    if (query.startsWith("unionId")) expect(props.fetchTasks).toHaveBeenCalledWith({ unionId: "用户+甲&乙" });
  }
);
