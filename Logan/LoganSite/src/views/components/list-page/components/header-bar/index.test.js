import { HeaderBar } from "./index";
import { parse } from "qs";

const create = filters => new HeaderBar({
  type: "native", pathname: "/native-list",
  filterConditions: { deviceId: "", platform: 0, beginTime: 1000, endTime: 2000, ...filters },
  updateFilterConditions: jest.fn(), fetchTasks: jest.fn()
});

test("platform selection requests immediately with the new platform and all conditions", () => {
  const bar = create({ appId: " app ", appVersion: "2.1.6", unionId: "用户+甲&乙" });
  bar.handlePlatformChange(3);
  expect(bar.props.updateFilterConditions).toHaveBeenCalledWith(expect.objectContaining({ platform: 3 }));
  expect(bar.props.fetchTasks).toHaveBeenCalledTimes(1);
  expect(bar.props.fetchTasks).toHaveBeenCalledWith({ platform: 3, beginTime: 1000, endTime: 2000,
    appId: "app", appVersion: "2.1.6", unionId: "用户+甲&乙" });
});

test.each(["appId", "appVersion", "unionId"])("%s can be searched without a device ID", key => {
  const bar = create({ [key]: "value" });
  bar.handleSearch();
  expect(bar.props.fetchTasks).toHaveBeenCalledWith({ [key]: "value", platform: 0, beginTime: 1000, endTime: 2000 });
});

test("all platforms sends zero and empty text conditions are omitted", () => {
  const bar = create({ platform: 3, appId: " " });
  bar.handlePlatformChange(0);
  expect(bar.props.fetchTasks).toHaveBeenCalledWith({ platform: 0, beginTime: 1000, endTime: 2000 });
});

test("share links round trip Chinese, plus and ampersand in search values", () => {
  const bar = create({ unionId: "用户+甲&乙", appId: "app", appVersion: "2.1.6" });
  expect(parse(bar.composeShareUrl().split("?")[1])).toEqual({ appId: "app", appVersion: "2.1.6",
    unionId: "用户+甲&乙", platform: "0", beginTime: "1000", endTime: "2000" });
});

test.each(["*张三*", "%张三%", "138_张*", "*用户+甲&乙%"])("unionId pattern %s survives search and sharing", unionId => {
  const bar = create({unionId: " " + unionId + " "});
  bar.handleSearch();
  expect(bar.props.fetchTasks).toHaveBeenCalledWith(expect.objectContaining({unionId}));
  expect(parse(bar.composeShareUrl().split("?")[1]).unionId).toBe(unionId);
});
