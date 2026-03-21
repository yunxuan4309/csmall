package com.cooxiao.mall.front.service.test;

import com.cooxiao.mall.front.MallFrontWebApiApplication;
import com.cooxiao.mall.pojo.front.vo.FrontCategoryTreeVO;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;


/*
* @RunWith(SpringRunner.class):指定springRunner作为加载Spring应用程序上下文,集成spring测试支持,
* @SpringBootTest(classes = MallFrontWebApiApplication.class)加载springboot应用程序上下文
* */
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
        System.out.println(redisTemplate.boundValueOps(key).get());
    }

    @Test
    public void saveAndGet(){
        redisTemplate.opsForValue().set("cqjtu:name","lihua");
        System.out.println(redisTemplate.opsForValue().get("cqjtu:name"));
    }

    @Test
    public void testMethods(){
        System.out.println("testMethod!");
    }
}
