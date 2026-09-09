import moment from "moment";

export const nativeTextFilters = ["deviceId", "appId", "appVersion", "unionId"];

export function nativeTaskFilters(filters) {
  const result = {};
  nativeTextFilters.forEach(key => {
    if (typeof filters[key] === "string" && filters[key].trim()) result[key] = filters[key].trim();
  });
  result.platform = Number(filters.platform || 0);
  ["beginTime", "endTime"].forEach(key => {
    if (filters[key] !== undefined && filters[key] !== null && filters[key] !== "") {
      result[key] = moment(filters[key]).valueOf();
    }
  });
  return result;
}
