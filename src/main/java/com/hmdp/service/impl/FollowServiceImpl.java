package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.constant.RedisConstants.FOLLOW_USER_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author kixuan
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    /**
     * 判断是否关注
     */
    @Override
    public Result isFollow(Long followUserId) {
        // 1. 获取当前登录用户
        long userId = UserHolder.getUser().getId();
        // 2. 从数据库中查是否有关注数据   --> 从redis中查
        // Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        // .isMember(key, value) 方法用于检查指定的 key 对应的集合中是否包含某个元素 value。
        Boolean member = stringRedisTemplate.opsForSet().isMember(FOLLOW_USER_KEY + userId, followUserId.toString());
        return Result.ok(member);
    }

    /**
     * 进行关注/取关操作
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        // 1. 获取当前登录用户
        long userId = UserHolder.getUser().getId();
        String key = FOLLOW_USER_KEY + userId;

        // 2. 判断是否关注
        if (isFollow) {
            // 未关注，进行关注操作
            Follow follow = new Follow().setUserId(userId).setFollowUserId(followUserId).setCreateTime(LocalDateTime.now());
            boolean isSuccess = save(follow);
            if (isSuccess) {
                // 成功的话就放在redis里面
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            } else {
                return Result.fail("关注失败，请稍后再试！");
            }
        } else {
            // 关注，进行取关操作  :数据库--redis
            // remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followUserId));
            stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
        }
        return Result.ok();
    }

    /**
     * 查看共同关注
     */
    @Override
    public Result followCommons(Long otherUserId) {
        // 1. 获取当前登录用户
        long userId = UserHolder.getUser().getId();
        String userKey = FOLLOW_USER_KEY + userId;
        String otherKey = FOLLOW_USER_KEY + otherUserId;

        //求交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(userKey, otherKey);
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 解析id集合
        // .stream() 方法将集合 intersect 转换为一个 Java 8 流（Stream）
        // .map(Long::valueOf) 是一个映射操作，它将集合中的每个元素从字符串转换为 Long 类型。
        // .collect(Collectors.toList()) 是一个收集操作，它将流中的元素收集到一个新的 List 集合中。这个新的 List 中包含了经过映射后的 Long 类型的元素。
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());

        // 查询用户
        // listByIds返回的是一个List<User>，所以需要进行转换
        // .stream() 方法将集合 ids 转换为一个 Java 8 流（Stream）
        // .map(user -> BeanUtil.copyProperties(user, UserDTO.class)) 是一个映射操作，它将集合中的每个元素从 User 类型转换为 UserDTO 类型。
        // .collect(Collectors.toList()将流中的映射后的 UserDTO 对象收集到一个新的 List<UserDTO> 中。这个操作将流转换为一个列表，其中包含了经过映射后的 UserDTO 对象。
        List<UserDTO> users = userService.listByIds(ids).stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(users);
    }
}
