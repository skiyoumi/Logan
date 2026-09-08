import {
  NATIVE_UPDATE_FILTER_CONDITIONS, NATIVE_CHANGE_LOADING,
  NATIVE_PAGE_REQUEST, NATIVE_PAGE_SUCCESS, NATIVE_PAGE_FAILURE
} from "./reducer";
import { fetchNativeTaskPageApi } from "../../../common/api";

let nextRequestId = 0;

function loadPage(page, pageSize, filters) {
  return dispatch => {
    const requestId = ++nextRequestId;
    dispatch({ type: NATIVE_PAGE_REQUEST, requestId });
    return fetchNativeTaskPageApi({ ...filters, page, pageSize }).then(result => {
      dispatch({ type: NATIVE_PAGE_SUCCESS, requestId, result, filters });
    }, error => {
      dispatch({ type: NATIVE_PAGE_FAILURE, requestId });
      throw error;
    });
  };
}

export function fetchInitData() {
  return loadPage(1, 20, {});
}

export function updateFilterConditions(filterConditions) {
  return dispatch => dispatch({ type: NATIVE_UPDATE_FILTER_CONDITIONS, filterConditions });
}

export function changeLoading(loading) {
  return dispatch => dispatch({ type: NATIVE_CHANGE_LOADING, loading });
}

export function fetchTasks(filters) {
  return (dispatch, getState) => loadPage(1, getState().nativeList.pagination.pageSize, filters)(dispatch);
}

export function fetchPage(page, pageSize) {
  return (dispatch, getState) => {
    const { appliedFilters, pagination } = getState().nativeList;
    return loadPage(pageSize === pagination.pageSize ? page : 1, pageSize, appliedFilters)(dispatch);
  };
}
