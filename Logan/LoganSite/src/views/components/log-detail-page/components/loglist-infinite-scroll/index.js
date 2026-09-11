import React, {Component} from "react";
import {Timeline, Divider, Spin, Icon, Button} from "antd";
import {isEqual, findIndex} from "lodash";
import {LOG_MOVE_DISTANCE, SCROLL_LOAD_THRESHOLD} from "./consts";
import {nativeLogTypeConfigs, webLogTypeConfigs} from "../../../../../consts/logtypes";
import {NUMBER_OF_LOG_IN_SINGLE_PAGE} from "../../../../../consts/pagination";
import moment from "moment";
import style from "./style.module.scss";

class LogListInfiniteScroll extends Component {
  constructor(props) {
    super(props);
    this.scrollContainer = React.createRef();
    this.lastScrollTop = 0;
    this.loading = false;
    this.state = {
      mouseUpRolling: false,
      uploading: false,
      downloading: false,
      loadError: null
    };
  }

  componentDidMount() {
    this.updateHighLightIndex();
  }

  componentWillUnmount() {
    this.unmounted = true;
  }

  shouldComponentUpdate(nextProps, nextState) {
    return !isEqual(nextProps, this.props) || !isEqual(nextState, this.state);
  }

  getSnapshotBeforeUpdate(prevProps) {
    if (!this.loading || prevProps.data === this.props.data || prevProps.data.length === 0 ||
        prevProps.focusLogId !== this.props.focusLogId) {
      return null;
    }
    const {scrollTop} = this.scrollContainer.current;
    const index = Math.min(Math.floor(scrollTop / LOG_MOVE_DISTANCE), prevProps.data.length - 1);
    return {
      id: prevProps.data[index].id,
      offset: scrollTop - index * LOG_MOVE_DISTANCE
    };
  }

  componentDidUpdate(prevProps, prevState, snapshot) {
    const {data, focusLogId, rollingListManually} = this.props;
    if (snapshot) {
      const index = this.calculateIndexOfLogList(snapshot.id, data);
      if (index !== -1) {
        const viewport = this.scrollContainer.current;
        viewport.scrollTop = index * LOG_MOVE_DISTANCE + snapshot.offset;
        this.lastScrollTop = viewport.scrollTop;
      }
    }
    if (prevProps.focusLogId !== focusLogId && focusLogId !== -1) {
      const focusLogIndex = this.calculateIndexOfLogList(focusLogId, data);
      if (focusLogIndex !== -1) rollingListManually(focusLogIndex);
    }
    this.updateHighLightIndex();
  }

  getLoadingOptions = () => {
    const {data, briefs} = this.props;
    const hasLogs = data.length > 0 && briefs.length > 0;
    return {
      upHasMore: hasLogs && data[0].id !== briefs[0].id,
      downHasMore: hasLogs && data[data.length - 1].id !== briefs[briefs.length - 1].id
    };
  };

  handleScroll = event => {
    const {scrollTop} = event.currentTarget;
    if (scrollTop !== this.lastScrollTop) {
      const isUp = scrollTop < this.lastScrollTop;
      this.lastScrollTop = scrollTop;
      this.setState({mouseUpRolling: isUp});
      this.loadNearBoundary(isUp);
    }
    this.updateHighLightIndex();
  };

  handleWheel = event => {
    if (event.deltaY === 0) return;
    const isUp = event.deltaY < 0;
    this.setState({mouseUpRolling: isUp});
    // A wheel gesture at an edge may not produce another scroll event.
    this.loadNearBoundary(isUp);
  };

  loadNearBoundary = isUp => {
    const {scrollTop, scrollHeight, clientHeight} = this.scrollContainer.current;
    if ((isUp ? scrollTop : scrollHeight - scrollTop - clientHeight) < SCROLL_LOAD_THRESHOLD) {
      this.loadMore(isUp ? "up" : "down");
    }
  };

  render() {
    const {data, focusLogId, type, updateFocusLogId} = this.props;
    const {mouseUpRolling, uploading, downloading, loadError} = this.state;
    const {upHasMore, downHasMore} = this.getLoadingOptions();
    return (
      <div className={style["inifinite-scroll-container"]}>
        <div className={style["container-inner"]} id="infinite-container-inner"
          ref={this.scrollContainer} onScroll={this.handleScroll} onWheel={this.handleWheel}>
          {mouseUpRolling && !upHasMore && <Divider className="bottom-line">顶部</Divider>}
          {upHasMore && (
            <div className="log-uploading-top">
              <div className="log-uploading-trigger" onClick={() => this.loadMore("up")}>
                {uploading ? <Spin size="small"/> : <Icon type="caret-up"/>}
              </div>
            </div>
          )}
          <div className={style["scroll-content"]}>
            <Timeline style={{marginTop: "20px"}}>
              {data.map((item, index) => (
                <TimelineItem
                  item={item}
                  index={index}
                  focusLogId={focusLogId}
                  type={type}
                  updateFocusLogId={updateFocusLogId}
                  key={`${item.id}-${index}`}
                />
              ))}
            </Timeline>
          </div>
          {downloading && <div className="demo-loading"><Spin/></div>}
          {loadError && (
            <div role="alert">
              日志加载失败，<Button size="small" onClick={() => this.loadMore(loadError)}>重试</Button>
            </div>
          )}
          {!downHasMore && <Divider className="bottom-line">到底了</Divider>}
        </div>
      </div>
    );
  }

  loadMore = direction => {
    const {briefs, data, fetchTaskDetail} = this.props;
    const {upHasMore, downHasMore} = this.getLoadingOptions();
    const isUp = direction === "up";
    if (this.loading || !(isUp ? upHasMore : downHasMore)) return;

    const edgeIndex = findIndex(briefs, item => item.id === (isUp ? data[0].id : data[data.length - 1].id));
    if (edgeIndex === -1) return;
    const pageSize = NUMBER_OF_LOG_IN_SINGLE_PAGE;
    const detailIds = (isUp ? briefs.slice(Math.max(0, edgeIndex - pageSize), edgeIndex) :
      briefs.slice(edgeIndex + 1, edgeIndex + 1 + pageSize)).map(item => item.id);
    if (detailIds.length === 0) return;

    // Lock synchronously until the request settles; never discard a load based on elapsed time.
    this.loading = true;
    this.setState({uploading: isUp, downloading: !isUp, loadError: null});
    return Promise.resolve()
      .then(() => fetchTaskDetail(detailIds, direction))
      .catch(() => {
        if (!this.unmounted) this.setState({loadError: direction});
      })
      .then(() => {
        this.loading = false;
        if (!this.unmounted) this.setState({uploading: false, downloading: false});
      });
  };

  // 在视窗中日志有变时触发
  updateHighLightIndex = () => {
    const {data, briefs, updateHighLightIndex} = this.props;
    let logHeadAndBottomInView = this.getLogHeadAndBottomInView(data, briefs);
    updateHighLightIndex(logHeadAndBottomInView.startLogIndex, logHeadAndBottomInView.endLogIndex);
  };

  // 获取视窗中的头尾日志id
  getLogHeadAndBottomInView = (logList, logIndexList) => {
    let scrollTopOfLogList = this.scrollContainer.current.scrollTop;
    let heightOfView = this.scrollContainer.current.clientHeight;

    let numberOfLogBeforeHead = Math.max(Math.round(scrollTopOfLogList / LOG_MOVE_DISTANCE), 0);
    let numberOfLogInView = Math.round(heightOfView / LOG_MOVE_DISTANCE);

    if (logList.length > 0 && logIndexList.length > 0) {
      let logHead = logList[Math.min(numberOfLogBeforeHead, logList.length - 1)].id;
      let logBottom = logList[Math.min(numberOfLogBeforeHead + numberOfLogInView, logList.length - 1)].id;

      return {
        startLogIndex: logIndexList.findIndex(logIndexItem => {
          return logIndexItem.id === logHead;
        }),
        endLogIndex: logIndexList.findIndex(logIndexItem => {
          return logIndexItem.id === logBottom;
        })
      };
    } else {
      return {
        startLogIndex: -1,
        endLogIndex: -1
      };
    }
  };

  calculateIndexOfLogList = (logId, details) => {
    return details.findIndex(item => item.id === logId);
  }
}

export default LogListInfiniteScroll;

/// 以下全为辅助组件
function TimelineItem({item, index, focusLogId, type, updateFocusLogId}) {
  const LogTypes = type === "native" ? nativeLogTypeConfigs : webLogTypeConfigs;
  let logType = LogTypes.find(type => type.logType === item.logType);
  if (logType === undefined) {
    logType = {
      logType: 0,
      logTypeName: "未知日志",
      displayColor: "#000000"
    };
  }
  return (
    <Timeline.Item
      className={style["timeline-item"]}
      id={"log-item-row-" + index}
      color={logType.displayColor}
      key={"log-item-row-" + item.id}
      onClick={e => {
        e.preventDefault();
        e.stopPropagation();
        updateFocusLogId(item.id);
      }}
    >
      <div
        style={{
          backgroundColor: item.id === focusLogId ? "#e6f7ff" : "transparent"
        }}
      >
        <div className={style["log-time-title"]}>
          <div className={style["log-time-title-left"]}>
            <div className={style["log-type"]} style={{color: logType.displayColor}}>
              {logType.logTypeName}:
            </div>
            <div className={style["log-time"]}>{moment(item.logTime).format("HH:mm:ss.SSS")}</div>
          </div>
          <div className={style["log-time-title-right"]}>
            <div className={style["log-id"]}>日志ID：{item.id}</div>
          </div>
        </div>
        <div className={style["log-content"]}>
          <div className={style["log-abbr"]}>{item.simpleContent || item.content}</div>
        </div>
      </div>
    </Timeline.Item>
  );

}
