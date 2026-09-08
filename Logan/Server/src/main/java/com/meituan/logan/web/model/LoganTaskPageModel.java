package com.meituan.logan.web.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

@Getter
@AllArgsConstructor
public class LoganTaskPageModel {
    private List<LoganTaskModel> items;
    private long total;
    private int page;
    private int pageSize;
}
