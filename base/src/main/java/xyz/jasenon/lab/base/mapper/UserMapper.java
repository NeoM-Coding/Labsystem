package xyz.jasenon.lab.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import xyz.jasenon.lab.base.api.model.User;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    User getUserByUsername(@Param("username") String username);

    boolean isNameExsist(@Param("name") String name);

    List<User> listUsers(@Param("keyword") String keyword);

}
