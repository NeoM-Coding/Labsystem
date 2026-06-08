package xyz.jasenon.lab.common.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private LocalDateTime createAt;
    private LocalDateTime updateAt;
    private LocalDateTime deleteAt;

}
