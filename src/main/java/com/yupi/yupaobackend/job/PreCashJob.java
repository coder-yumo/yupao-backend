package com.yupi.yupaobackend.job;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.yupaobackend.model.domain.User;
import com.yupi.yupaobackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.yupi.yupaobackend.constant.RedisConstant.USER_SEARCH_KEY;

/**
 * 缓存预热
 */
@Component
@Slf4j
public class PreCashJob {

    @Resource
    RedisTemplate redisTemplate;

    @Resource
    UserService userService;

    @Resource
    RedissonClient redissonClient;

//    List<Long> mainUserList = Collections.singletonList(3L);

    // 每天晚上 23:30 执行一次任务
    @Scheduled(cron = "0 57 22 * * ?")
    public void doCashRecommendTask() {
        RLock lock = redissonClient.getLock("yupao:prechchsjob:docache:lock");

        try {
            //只有一个线程会获取锁
            if (lock.tryLock(0, -1, TimeUnit.MILLISECONDS)) {
                List<Long> mainUserList = new ArrayList<>();
                QueryWrapper<User> queryWrapper1 = new QueryWrapper<>();
                Page<User> userPage1 = userService.page(new Page<>(1, 20), queryWrapper1);
                for (User user : userPage1.getRecords()) {
                    mainUserList.add(user.getId());
                }
                for (Long userId : mainUserList) {
                    //没有直接查询数据库
                    QueryWrapper<User> queryWrapper = new QueryWrapper<>();
                    Page<User> userPage = userService.page(new Page<>(1, 20), queryWrapper);
                    // 执行你的任务逻辑
                    String key = USER_SEARCH_KEY + userId;

                    //写缓存
                    try {
                        redisTemplate.opsForValue().set(key, userPage, 12, TimeUnit.HOURS);
                    } catch (Exception e) {
                        log.error("redis set key error");
                    }
                }
            }

        } catch (InterruptedException e) {
            log.error("redis set key error");
        } finally {
            //只能释放自己的锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

    }
}
