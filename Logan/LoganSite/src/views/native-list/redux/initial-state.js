import moment from "moment";

export const initialState = {
  filterConditions: {
    deviceId: "",
    appId: "",
    appVersion: "",
    unionId: "",
    platform: 0,
    beginTime: moment().startOf("day").subtract(7, 'days'),
    endTime: moment().startOf("day")
  },
  tasks: [],
  listView: null,
  pagination: { current: 1, pageSize: 20, total: 0 },
  appliedFilters: {},
  requestId: 0,
  loading: false
};