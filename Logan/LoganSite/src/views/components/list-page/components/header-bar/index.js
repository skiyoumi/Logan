import React, { Component } from "react";
import {connect} from "react-redux";
import PropTypes from "prop-types";
import { Input, Select, DatePicker, Icon, Layout, Button, message } from "antd";
import "antd/dist/antd.css";
import "./style.scss";
import ClickShare from "../../../../../common/components/ClickShare/ClickShare"
import moment from "moment";
import { stringify } from "qs";
import { nativeTaskFilters } from "../../../../../common/task-filters";

const { Option } = Select;
const { RangePicker } = DatePicker;
const { Header } = Layout;

export class HeaderBar extends Component {

  static propTypes = {
    filterConditions: PropTypes.object,
    updateFilterConditions: PropTypes.func,
    fetchTasks: PropTypes.func,
    type: PropTypes.string
  };

  static defaultProps = {
    filterConditions: {
      deviceId: "",
      platform: 0,
      beginTime: moment().startOf("week"),
      endTime: moment().startOf("day")
    },
    updateFilterConditions: null,
    fetchTasks: null
  };

  render() {
    const { filterConditions, type, updateFilterConditions } = this.props;
    return (
      <Header className={`header ${type === "native" ? "native-tasklist-header" : ""}`}>
        <div className="tasklist-filterbar-container">
          <Input.Group compact={type !== "native"} className="filterbar-group">
            {
              type === "native" &&
              <Select
                data-test="platform-selector"
                value={filterConditions.platform}
                onChange={this.handlePlatformChange}
                style={{ minWidth: "100px" }}
              >
                <Option value={0}>全部平台</Option>
                <Option value={1}>Android</Option>
                <Option value={2}>iOS</Option>
                <Option value={3}>鸿蒙 HarmonyOS</Option>
              </Select>
            }
            <RangePicker
              data-test="range-picker"
              allowClear={false}
              format="YYYY-MM-DD"
              placeholder={["起始时间", "结束时间"]}
              onChange={this.handleTimeRangeChange}
              value={[moment(filterConditions.beginTime), moment(filterConditions.endTime)]}
              style={{ minWidth: "240px" }}
            />
            <Input
              data-test="deviceId-input"
              className="filter-input"
              placeholder="设备编号"
              value={filterConditions.deviceId}
              onChange={this.handleDeviceIdChange}
              onPressEnter={this.handleSearch}
              suffix={
                filterConditions.deviceId ? (
                  <Icon
                    data-test="clean-deviceId-icon"
                    className="empty-search"
                    key="empty-search"
                    type="close-circle"
                    onClick={this.handleCleanDeviceId}
                  />
                ) : (
                  <span />
                )
              }
            />
            {type === "native" && ["appId", "appVersion", "unionId"].map((key, index) => (
              <Input
                key={key}
                data-test={key + "-input"}
                className="filter-input"
                placeholder={["AppId", "AppVersion", "unionId（支持 *、%）"][index]}
                title={key === "unionId" ? "* 或 % 匹配任意文本，如 *张三*、138*；不带通配符精确匹配，下划线按普通字符查询" : undefined}
                aria-label={["AppId", "AppVersion", "unionId"][index]}
                maxLength={key === "appVersion" ? 64 : 256}
                allowClear
                value={filterConditions[key] || ""}
                onChange={event => updateFilterConditions({ ...filterConditions, [key]: event.target.value })}
                onPressEnter={this.handleSearch}
              />
            ))}
          </Input.Group>
          <Button data-test="search-button" icon="search" type="primary" onClick={this.handleSearch}>
            搜索
          </Button>
        </div>
        <ClickShare buttonId={"share-button"} shareUrl={this.composeShareUrl()} buttonText={"分享"} />
      </Header>
    );
  }

  composeShareUrl = () => {
    const {filterConditions, type, pathname} = this.props;
    if (type === "native") {
      return `${window.location.origin}/#${pathname}?${stringify(nativeTaskFilters(filterConditions))}`
    } else {
      return `${window.location.origin}/#${pathname}?deviceId=${filterConditions.deviceId}&beginTime=${moment(filterConditions.beginTime).valueOf()}&endTime=${moment(filterConditions.endTime).valueOf()}`
    }
  };

  // event handlers
  handleSearch = () => {
    const { filterConditions, fetchTasks, type } = this.props;
    if (type !== "native" && filterConditions.deviceId === "") {
      message.error("必须填写设备编号才能进行查询！");
      return;
    }
    if (type === "native") {
      return fetchTasks(nativeTaskFilters(filterConditions));
    } else {
      fetchTasks({
        deviceId: filterConditions.deviceId,
        beginTime: moment(filterConditions.beginTime).valueOf(),
        endTime: moment(filterConditions.endTime).valueOf()
      })
    }

  };

  handleDeviceIdChange = e => {
    const { filterConditions, updateFilterConditions } = this.props;

    updateFilterConditions({
      ...filterConditions,
      deviceId: e.target.value
    });
  };

  handleCleanDeviceId = () => {
    const { filterConditions, updateFilterConditions } = this.props;

    updateFilterConditions({
      ...filterConditions,
      deviceId: ""
    });
  };

  handlePlatformChange = value => {
    const { filterConditions, updateFilterConditions } = this.props;

    const nextFilters = { ...filterConditions, platform: value };
    updateFilterConditions(nextFilters);
    return this.props.fetchTasks(nativeTaskFilters(nextFilters));
  };

  handleTimeRangeChange = value => {
    const { filterConditions, updateFilterConditions } = this.props;
    const [beginMoment, endMoment] = value;

    if (endMoment.diff(beginMoment) >= 7 * 86400000) {
      message.warn("请保证选择的时间范围不超过7天");
      return;
    }
    updateFilterConditions({
      ...filterConditions,
      beginTime: beginMoment.valueOf(),
      endTime: endMoment.valueOf()
    });

  };
}

function mapStateToProps(state) {
  return {
    pathname: state.router.location.pathname,
    search: state.router.location.search,
    hash: state.router.location.hash,
  }
}

export default connect(mapStateToProps)(HeaderBar);
