import {UPDATE_LIST_VIEW} from "../../../common/list-view";
import {initialState} from "./initial-state";


export const NATIVE_PAGE_REQUEST = "NATIVE_PAGE_REQUEST";
export const NATIVE_PAGE_SUCCESS = "NATIVE_PAGE_SUCCESS";
export const NATIVE_PAGE_FAILURE = "NATIVE_PAGE_FAILURE";
export const NATIVE_UPDATE_TASKS = "NATIVE_UPDATE_TASKS";
export const NATIVE_UPDATE_FILTER_CONDITIONS = "NATIVE_UPDATE_FILTER_CONDITIONS";
export const NATIVE_CHANGE_LOADING = "NATIVE_CHANGE_LOADING";

export default (state = initialState, action) => {
  switch (action.type) {
    case UPDATE_LIST_VIEW:
      if (action.listType !== "native") return state;
      return { ...state, listView: action.listView,
        ...(action.listView ? {requestId: 0, loading: false} : {}) };

    case NATIVE_PAGE_REQUEST:
      return { ...state, loading: true, requestId: action.requestId };
    case NATIVE_PAGE_SUCCESS:
      if (state.requestId !== action.requestId) return state;
      return {
        ...state, loading: false, tasks: action.result.items,
        pagination: { current: action.result.page, pageSize: action.result.pageSize, total: action.result.total },
        appliedFilters: action.filters
      };
    case NATIVE_PAGE_FAILURE:
      return state.requestId === action.requestId ? { ...state, loading: false } : state;
    case NATIVE_UPDATE_TASKS:
      return {
        ...state,
        tasks: action.tasks
      };
    case NATIVE_UPDATE_FILTER_CONDITIONS:
      return {
        ...state,
        filterConditions: action.filterConditions
      };
    case NATIVE_CHANGE_LOADING:
      return {
        ...state,
        loading: action.loading
      };
    default:
      return state;
  }
};
