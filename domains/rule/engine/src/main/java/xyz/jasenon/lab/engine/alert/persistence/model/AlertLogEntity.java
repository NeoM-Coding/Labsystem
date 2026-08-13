package xyz.jasenon.lab.engine.alert.persistence.model;

import com.baomidou.mybatisplus.annotation.TableName;
import xyz.jasenon.lab.persistence.model.BaseEntity;

import java.time.Instant;

@TableName("alert_log")
public class AlertLogEntity extends BaseEntity {

    private String eventId;
    private String runtimeId;
    private String actionGroupId;
    private String deviceConditionGroupId;
    private String timeConditionGroupId;
    private Instant matchedAt;
    private Instant completedAt;
    private String status;
    private String content;
    private String userIds;
    private String actions;

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getRuntimeId() { return runtimeId; }
    public void setRuntimeId(String runtimeId) { this.runtimeId = runtimeId; }
    public String getActionGroupId() { return actionGroupId; }
    public void setActionGroupId(String actionGroupId) { this.actionGroupId = actionGroupId; }
    public String getDeviceConditionGroupId() { return deviceConditionGroupId; }
    public void setDeviceConditionGroupId(String deviceConditionGroupId) { this.deviceConditionGroupId = deviceConditionGroupId; }
    public String getTimeConditionGroupId() { return timeConditionGroupId; }
    public void setTimeConditionGroupId(String timeConditionGroupId) { this.timeConditionGroupId = timeConditionGroupId; }
    public Instant getMatchedAt() { return matchedAt; }
    public void setMatchedAt(Instant matchedAt) { this.matchedAt = matchedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getUserIds() { return userIds; }
    public void setUserIds(String userIds) { this.userIds = userIds; }
    public String getActions() { return actions; }
    public void setActions(String actions) { this.actions = actions; }
}
