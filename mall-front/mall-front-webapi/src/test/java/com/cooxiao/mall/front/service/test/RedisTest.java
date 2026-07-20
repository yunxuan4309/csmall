package com.cooxiao.mall.front.service.test;

import com.cooxiao.mall.front.MallFrontWebApiApplication;
import com.cooxiao.mall.pojo.front.vo.FrontCategoryTreeVO;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;


@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = MallFrontWebApiApplication.class)
public class RedisTest {

    @Autowired
    private RedisTemplate redisTemplate;

    @Test
    public void save(){
        String key="key1";
        FrontCategoryTreeVO frontCategoryTreeVO=new FrontCategoryTreeVO();
        redisTemplate.boundValueOps(key).set(frontCategoryTreeVO);
        log.info("redis get result: {}", redisTemplate.boundValueOps(key).get());
    }

    @Test
    public void saveAndGet(){
        redisTemplate.opsForValue().set("cqjtu:name","lihua");
        log.info("redis get result: {}", redisTemplate.opsForValue().get("cqjtu:name"));
    }

    @Test
    public void testMethods(){
        log.info("testMethod!");
    }
}
