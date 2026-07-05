package xyz.jasenon.lab.engine.action;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 通知动作模型；通道尚未接入，但完整保留用户、通知类型和内容。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReportAction implements Action{

    private String actionGroupId;
    private List<String> userIds = new ArrayList<>();
    private Set<ReportType> types = EnumSet.noneOf(ReportType.class);
    private String content;

    public enum ReportType {
        SMS,
        SMTP
    }

    @Override
    public ActionType is() {
        return ActionType.Report;
    }
}
