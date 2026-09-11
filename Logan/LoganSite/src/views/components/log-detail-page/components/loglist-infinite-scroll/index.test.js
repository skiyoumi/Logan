import React from "react";
import ReactDOM from "react-dom";
import {act} from "react-dom/test-utils";
import LogListInfiniteScroll from "./index";
import reducer, {NATIVE_UPDATE_TASK_DETAIL} from "../../../../native-log-detail/redux/reducer";

let container;
let viewport;
let props;
let details;
let now;

const render = () => ReactDOM.render(<LogListInfiniteScroll {...props} data={details}/>, container);
const flush = async () => {
  await act(async () => { await Promise.resolve(); });
};
const scrollTo = top => {
  act(() => {
    viewport.scrollTop = top;
    viewport.dispatchEvent(new Event("scroll"));
  });
};
const scrollToBottom = () => scrollTo(viewport.scrollHeight - viewport.clientHeight);

beforeEach(() => {
  now = 10000;
  jest.spyOn(Date, "now").mockImplementation(() => now);
  jest.spyOn(HTMLElement.prototype, "offsetParent", "get").mockImplementation(function () {
    return this.parentNode;
  });
  jest.spyOn(Element.prototype, "clientHeight", "get").mockReturnValue(560);
  jest.spyOn(Element.prototype, "scrollHeight", "get").mockImplementation(function () {
    return this.querySelectorAll(".ant-timeline-item").length * 56 + 20;
  });
  container = document.createElement("div");
  document.body.appendChild(container);
  details = [];
  props = {
    type: "native",
    briefs: Array.from({length: 400}, (_, index) => ({
      id: index + 1, logType: 1, logTime: 0, content: `Log ${index + 1}`
    })),
    focusLogId: -1,
    updateFocusLogId: jest.fn(),
    updateHighLightIndex: jest.fn(),
    rollingListManually: jest.fn(index => { viewport.scrollTop = Math.max(0, (index - 1) * 56); }),
    fetchTaskDetail: jest.fn((ids, direction) => Promise.resolve().then(() => {
      details = reducer({taskDetails: details}, {
        type: NATIVE_UPDATE_TASK_DETAIL,
        taskDetails: ids.map(id => props.briefs.find(item => item.id === id)),
        direction
      }).taskDetails;
      render();
    }))
  };
  act(() => { render(); });
  viewport = container.querySelector("#infinite-container-inner");
  details = props.briefs.slice(0, 75);
  act(() => { render(); });
});

afterEach(() => {
  act(() => { ReactDOM.unmountComponentAtNode(container); });
  container.remove();
  jest.restoreAllMocks();
});

test("scrolling to the bottom immediately after opening still loads the next logs", async () => {
  scrollToBottom();
  await flush();
  expect(details[details.length - 1].id).toBe(100);
});

test("fast consecutive page loads do not leave the list stuck at the bottom", async () => {
  now += 2000;
  scrollToBottom();
  await flush();
  expect(details[details.length - 1].id).toBe(100);
  scrollToBottom();
  await flush();
  now += 2000;
  scrollToBottom();
  await flush();
  expect(details[details.length - 1].id).toBeGreaterThanOrEqual(125);
});

test("dragging the scrollbar down after scrolling up loads later logs", async () => {
  now += 2000;
  scrollTo(1000);
  act(() => {
    const event = new Event("mousewheel", {bubbles: true});
    Object.defineProperty(event, "wheelDelta", {value: 120});
    viewport.dispatchEvent(event);
  });
  scrollTo(900);
  scrollToBottom();
  await flush();
  expect(props.fetchTaskDetail).toHaveBeenCalledWith(
    props.briefs.slice(75, 100).map(item => item.id), "down"
  );
});

test("loading beyond the 150-row window preserves the visible log", async () => {
  details = props.briefs.slice(0, 150);
  act(() => { render(); });
  now += 2000;
  scrollToBottom();
  const visibleId = details[Math.floor(viewport.scrollTop / 56)].id;
  await flush();
  expect(details[Math.floor(viewport.scrollTop / 56)].id).toBe(visibleId);
});

test("scrolling upward prepends earlier logs without moving the visible log", async () => {
  details = props.briefs.slice(100, 250);
  act(() => { render(); });
  scrollTo(1000);
  scrollTo(20);
  const visibleId = details[Math.floor(viewport.scrollTop / 56)].id;
  await flush();
  expect(details[0].id).toBe(76);
  expect(details[Math.floor(viewport.scrollTop / 56)].id).toBe(visibleId);
});

test.each(["ascending", "descending"])("can reach the final log in %s order without skipping a page", async order => {
  if (order === "descending") props.briefs = props.briefs.slice().reverse();
  details = props.briefs.slice(0, 75);
  act(() => { render(); });
  for (let page = 0; page < 13; page++) {
    scrollToBottom();
    await flush();
  }
  expect(details[details.length - 1].id).toBe(props.briefs[399].id);
  expect(props.fetchTaskDetail.mock.calls.reduce((ids, call) => ids.concat(call[0]), [])).toEqual(
    props.briefs.slice(75).map(item => item.id)
  );
  expect(container.textContent).toContain("到底了");
  scrollToBottom();
  await flush();
  expect(props.fetchTaskDetail).toHaveBeenCalledTimes(13);
});

test("a rejected request can be retried with a wheel gesture at the same bottom edge", async () => {
  props.fetchTaskDetail.mockRejectedValueOnce(new Error("network failure"));
  scrollToBottom();
  await flush();
  expect(container.querySelector('[role="alert"]')).not.toBeNull();
  act(() => {
    viewport.dispatchEvent(new WheelEvent("wheel", {bubbles: true, deltaY: 120}));
  });
  await flush();
  expect(details[details.length - 1].id).toBe(100);
  expect(container.querySelector('[role="alert"]')).toBeNull();
});

test("repeated gestures while a request is pending only fetch one page", async () => {
  let finish;
  props.fetchTaskDetail.mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
  scrollToBottom();
  await flush();
  act(() => {
    for (let i = 0; i < 5; i++) {
      viewport.dispatchEvent(new WheelEvent("wheel", {bubbles: true, deltaY: 120}));
    }
  });
  expect(props.fetchTaskDetail).toHaveBeenCalledTimes(1);
  finish();
  await flush();
});

test("a partial last page is requested once without duplicating existing logs", async () => {
  props.briefs = props.briefs.slice(0, 83);
  details = props.briefs.slice(0, 70);
  act(() => { render(); });
  scrollToBottom();
  await flush();
  expect(props.fetchTaskDetail).toHaveBeenCalledWith(props.briefs.slice(70).map(item => item.id), "down");
  expect(details.map(item => item.id)).toEqual(props.briefs.map(item => item.id));
});
