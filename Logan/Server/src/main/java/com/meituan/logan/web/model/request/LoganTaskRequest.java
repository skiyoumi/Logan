package com.meituan.logan.web.model.request;

import com.meituan.logan.web.enums.PlatformEnum;
import com.meituan.logan.web.util.DateTimeUtil;
import com.meituan.logan.web.util.TypeSafeUtil;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

/**
 * 功能描述:  <p></p>
 *
 * @version 1.0 2019-10-07
 * @since logan-web 1.0
 */
@Data
public class LoganTaskRequest {
    private String deviceId;

    private Long beginTime;

    private Long endTime;

    private Integer platform;

    private String appId;

    private String appVersion;

    private Long taskId;

    private String unionId;

    /**
     * 调整参数
     */
    public void ready() {
        this.deviceId = StringUtils.trimToNull(this.deviceId);
        this.appId = StringUtils.trimToNull(this.appId);
        this.appVersion = StringUtils.trimToNull(this.appVersion);
        this.unionId = StringUtils.trimToNull(this.unionId);
        this.platform = TypeSafeUtil.nullToDefault(this.platform, PlatformEnum.ALL.getPlatform());
        this.endTime = this.endTime == null
                ? System.currentTimeMillis()
                : this.endTime + DateTimeUtil.ONE_DAY;
        this.beginTime = TypeSafeUtil.nullToDefault(this.beginTime,
                this.endTime - TypeSafeUtil.SEVEN_DAY);
    }

    public LoganTaskRequest(String deviceId, Long beginTime, Long endTime, Integer platform,
                            String appId, String appVersion, Long taskId, String unionId) {
        this.deviceId = deviceId;
        this.beginTime = beginTime;
        this.endTime = endTime;
        this.platform = platform;
        this.appId = appId;
        this.appVersion = appVersion;
        this.taskId = taskId;
        this.unionId = unionId;
    }
}
