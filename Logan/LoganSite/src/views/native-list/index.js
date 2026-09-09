import React, { Component } from "react";
import { connect } from "react-redux";
import { parse } from "qs";
import moment from "moment";
import { nativeTextFilters } from "../../common/task-filters";
import ListPage from "../components/list-page/index";
import { updateFilterConditions, fetchTasks, fetchInitData, fetchPage } from "./redux/action";
import {message} from "antd";


export class NativeList extends Component {

  componentDidCatch(error, errorInfo) {
    message.error("页面异常！");
    this.props.history.replace("/");
  }

  componentDidMount() {
    const { updateFilterConditions, fetchTasks, fetchInitData } = this.props;

    let params = {};
    if (this.props.location) {
      params = parse(this.props.location.search, { ignoreQueryPrefix: true });
    }
    
    const filters = {
      deviceId: "", appId: "", appVersion: "", unionId: "", platform: 0,
      beginTime: moment().startOf("day").subtract(6, "days").valueOf(),
      endTime: moment().startOf("day").valueOf()
    };
    const query = {};
    nativeTextFilters.forEach(key => {
      if (typeof params[key] === "string") query[key] = params[key];
    });
    ["platform", "beginTime", "endTime"].forEach(key => {
      if (typeof params[key] === "string" && params[key] !== "" && Number.isFinite(Number(params[key]))) {
        query[key] = Number(params[key]);
      }
    });
    updateFilterConditions({ ...filters, ...query });
    if (Object.keys(query).length) {
      fetchTasks(query);
    } else {
      fetchInitData();
    }

  }  

  render() {
    return (
      <ListPage
        {...this.props}
        data-test="ListPage"
        type="native"
        detailUrlPrefix="/native-log-detail"
      />
    )
  }
}

export function mapStateToProps(state) {
  return {
    ...state.nativeList
  };
}

export function mapDispatchToProps(dispatch) {
  return {
    updateFilterConditions: newFilterConditions => dispatch(updateFilterConditions(newFilterConditions)),
    fetchTasks: (filterConditions) => dispatch(fetchTasks(filterConditions)),
    fetchInitData: () => dispatch(fetchInitData()),
    onPageChange: (page, pageSize) => dispatch(fetchPage(page, pageSize))
  };
}

export default connect(
  mapStateToProps,
  mapDispatchToProps
)(NativeList);