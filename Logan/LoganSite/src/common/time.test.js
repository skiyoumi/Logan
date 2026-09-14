import {stringifyTime} from "./time";


describe("stringifyTime function", () => {
  it("should show milisecond", () => {
    const date = new Date(2019, 10, 11, 10, 8, 23, 79);
    expect(stringifyTime(date, false)).toBe("10:08:23.79")
  });

  it("shouldn't show milisecond", () => {
    const date = new Date(2019, 10, 11, 10, 8, 23, 79);
    expect(stringifyTime(date, true)).toBe("10:08:23")
  })
})
