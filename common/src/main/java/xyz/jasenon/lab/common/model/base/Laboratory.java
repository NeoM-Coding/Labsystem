package xyz.jasenon.lab.common.model.base;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xyz.jasenon.lab.common.model.BaseEntity;
import xyz.jasenon.lab.common.model.Error;

import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@TableName(value = "laboratory", autoResultMap = true)
@Data
public class Laboratory extends BaseEntity {

    // buildingName 所属楼栋名称  用来filter utf8
    private String buildingName;

    // orgName 所属单位名称  用来filter
    // 全局string pool 用redis set永存 重启服务重建
    private String orgName;

    // 实验室名称
    private String laboratoryName;

    // extra 一些动态配置
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extra;

    // manager 实验室负责人
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<User> manager;

    public Error validate(){
        Error error = new Error();
        if (this.laboratoryName.isBlank()){
            error.append("实验室名称不得为空");
        }
        if (this.buildingName.isBlank()){
            error.append("实验室所在楼栋不得为空");
        }
        return error;
    }


}
