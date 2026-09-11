import {canRestoreListView, getListUrl} from "../../../common/list-view";
import {stringify} from "qs";
import React, { Component } from "react";
import { Table, Icon, Button } from "antd";
import moment from "moment";
import "antd/dist/antd.css";
import "./style.scss";
import HeaderBar from "./components/header-bar/index";

const { Column } = Table;

const ICON_BY_PLATFORM = {
  "3": <span>HarmonyOS</span>,
  "-1": <span>Unknown</span>,
  "0": (
    <span>
      <Icon type="android" /> | <Icon type="apple" /> | HarmonyOS
    </span>
  ),
  "1": (
    <span>
      <Icon type="android" />
    </span>
  ),
  "2": (
    <span>
      <Icon type="apple" />
    </span>
  )
};

class ListPage extends Component {
  constructor(props) {
    super(props);
    this.tableContainer = React.createRef();
    this.state = {
      pagination: canRestoreListView(props) ? props.listView.pagination : {current: 1, pageSize: 20}
    };
  }

  componentDidMount() {
    if (canRestoreListView(this.props)) {
      const {scrollTop, scrollLeft, containerScrollTop} = this.props.listView;
      const container = this.tableContainer.current;
      const body = container.querySelector(".ant-table-body");
      if (body) {
        body.scrollTop = scrollTop;
        body.scrollLeft = scrollLeft;
      }
      container.scrollTop = containerScrollTop;
    }
  }

  componentDidUpdate(prevProps) {
    // Web logs are paginated in the table; new search results start on page one.
    if (!this.props.onPageChange && prevProps.tasks !== this.props.tasks && this.state.pagination.current !== 1) {
      this.setState({pagination: {...this.state.pagination, current: 1}});
    }
  }

  handlePageChange = (page, pageSize) => {
    if (this.props.onPageChange) return this.props.onPageChange(page, pageSize);
    this.setState(({pagination}) => ({pagination: {
      current: pageSize === pagination.pageSize ? page : 1, pageSize
    }}));
  };

  render() {
    const { filterConditions, tasks, updateFilterConditions, fetchTasks, loading, type } = this.props;
    return (
      <div className={"listpage-container"}>
        <HeaderBar
          filterConditions={filterConditions}
          updateFilterConditions={updateFilterConditions}
          fetchTasks={fetchTasks}
          type={type}
        />
        <div className={"table-container"} ref={this.tableContainer}>
            {
              (() => {
                if (type === "native") {
                  return this.renderNativeColumns(tasks, loading);
                } else if (type === "web") {
                  return this.renderWebColumns(tasks, loading);
                }
              })()
            }
        </div>
      </div>
    );
  }

  getPagination = () => ({
    defaultPageSize: 20,
    showSizeChanger: true,
    showQuickJumper: true,
    pageSizeOptions: ["10", "20", "50", "100"],
    showTotal: total => `共 ${total} 条`,
    ...this.state.pagination,
    ...this.props.pagination,
    onChange: this.handlePageChange,
    onShowSizeChange: this.handlePageChange
  });

  renderNativeColumns = (tasks, loading) => {
    return (
      <Table size="middle" dataSource={tasks} loading={loading} pagination={this.getPagination()} scroll={{y: "calc(100vh - 320px)"}} rowKey="taskId">
        <Column title="任务id" dataIndex="taskId" key="taskId" width="10%" />
        <Column title="AppId" dataIndex="appId" key="appId" width="10%"/>
        <Column title="AppVersion" dataIndex="appVersion" key="appVersion" width="10%"/>
        <Column title="设备标识" dataIndex="deviceId" key="deviceId" width="15%" />
        <Column title="unionId" dataIndex="unionId" key="unionId" width="15%" />
        <Column title="设备平台" dataIndex="platform" key="platform" width="10%" render={this.renderColumnPlatform} />
        <Column title="日志当天时间" dataIndex="logDate" key="logDate" width="10%" render={this.renderColumnLogDate} />
        <Column title="日志上报时间" dataIndex="addTime" key="addTime" width="10%" render={this.renderColumnAddTime} />
        <Column title="操作" dataIndex="action" width="10%" render={this.renderColumnAction} />
      </Table>
    );
  };

  renderWebColumns = (tasks, loading) => {
    return (
      <Table size="middle" dataSource={tasks} loading={loading} pagination={this.getPagination()} scroll={{y: "calc(100vh - 280px)"}} rowKey="taskId">
        <Column title="设备标识" dataIndex="deviceId" key="deviceId" width="30%" />
        <Column title="日志来源" dataIndex="webSource" key="webSource" width="15%" render={this.renderColumnWebSource} />
        <Column title="环境信息" dataIndex="environment" key="environment" width="15%" render={this.renderColumnEnvironment} />
        <Column title="日志当天时间" dataIndex="logDate" key="logDate" width="10%" render={this.renderColumnLogDate} />
        <Column title="日志上报时间" dataIndex="addTime" key="addTime" width="10%" render={this.renderColumnAddTime} />
        <Column title="操作" dataIndex="action" width="10%" render={this.renderColumnAction} />
      </Table>
    );
  };

  renderColumnPlatform = platform => {
    return ICON_BY_PLATFORM[platform]
  };

  renderColumnWebSource = webSource => {
    if (webSource === null) {
      return <span>-</span>
    } else {
      return <span>{webSource}</span>
    }
  };

  renderColumnEnvironment = environment => {
    if (environment === null) {
      return <span>-</span>
    } else {
      return <span>{environment}</span>
    }
  };

  renderColumnLogDate = logDate => {
    return <div>{moment(logDate).format("YYYY-MM-DD")}</div>
  }

  renderColumnAddTime = addTime => {
    return <div>{moment(addTime).format("YYYY-MM-DD HH:mm:ss.SSS")}</div>;
  };

  renderColumnAction = (text, record, index) => {
    const {type} = this.props;
    if (type === "native") {
      return <Button onClick={this.toDetail(record.taskId)}>日志详情</Button>;
    } else {
      return <Button onClick={this.toDetail(record.tasks)}>日志详情</Button>;
    }
  };

  toDetail = tasks => () => {
    const {detailUrlPrefix, history, location, updateListView} = this.props;
    if (updateListView && location) {
      const container = this.tableContainer.current;
      const body = container.querySelector(".ant-table-body");
      const {current, pageSize} = this.getPagination();
      updateListView({
        url: getListUrl(location), tasks: String(tasks), pagination: {current, pageSize},
        scrollTop: body ? body.scrollTop : 0,
        scrollLeft: body ? body.scrollLeft : 0,
        containerScrollTop: container.scrollTop
      });
    }
    history.push(`${detailUrlPrefix}?${stringify({tasks})}`);
  };
}

export default ListPage;
