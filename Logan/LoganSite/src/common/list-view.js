export const UPDATE_LIST_VIEW = "UPDATE_LIST_VIEW";

export const updateListView = (listType, listView) => ({type: UPDATE_LIST_VIEW, listType, listView});

export const getListUrl = location => `${location.pathname}${location.search || ""}`;

export const canRestoreListView = ({listView, location}) =>
  !!(listView && location && listView.url === getListUrl(location));
