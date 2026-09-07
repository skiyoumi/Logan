import ConnectedHeaderBar from "./index";

const HeaderBar = ConnectedHeaderBar.WrappedComponent;

describe("native platform filter", () => {
  it("searches immediately with only the newly selected platform", () => {
    const updateFilterConditions = jest.fn();
    const fetchTasks = jest.fn();
    const filterConditions = {
      deviceId: "",
      appId: "",
      appVersion: "",
      taskId: "",
      unionId: "",
      platform: 0,
      beginTime: 1000,
      endTime: 2000
    };
    const component = new HeaderBar({
      type: "native",
      filterConditions,
      updateFilterConditions,
      fetchTasks
    });

    component.handlePlatformChange(3);

    expect(updateFilterConditions).toHaveBeenCalledWith({
      ...filterConditions,
      platform: 3
    });
    expect(fetchTasks).toHaveBeenCalledWith({
      deviceId: "",
      appId: "",
      appVersion: "",
      taskId: "",
      unionId: "",
      platform: 3,
      beginTime: 1000,
      endTime: 2000
    });
  });
});
