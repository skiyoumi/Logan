import moment from "moment";

export const initialState = {
  filterConditions: {
    deviceId: "",
    platform: 0,
    beginTime: moment().startOf("day").subtract(7, 'days'),
    endTime: moment().startOf("day")
  },
  tasks: [],
  pagination: { current: 1, pageSize: 20, total: 0 },
  appliedFilters: {},
  requestId: 0,
  loading: false
};