package com.cooxiao.mall.seckill.timer.config;

import com.cooxiao.mall.seckill.timer.job.SeckillBloomInitialJob;
import com.cooxiao.mall.seckill.timer.job.SeckillInitialJob;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author=java.cooxiao.com QQ:25380243
 * @since=2024/9/29
 */
@Configuration
public class QuartzConfig {
    // 向Spring容器中保存JobDetail对象
    @Bean
    public JobDetail initJobDetail(){
        return JobBuilder.newJob(SeckillInitialJob.class)
                .withIdentity("initJobDetail")
                .storeDurably()
                .build();
    }
    // 向Spring容器中保存Trigger对象进行触发
    @Bean
    public Trigger initTrigger(){
        // 12:00  14:00  16:00  18:00 进行秒杀,提前五分钟的cron表达式
        // 0 55 11,13,15,17 * * ?
        // 学习过程中,为了测试个观察效果,我们设计每分钟运行一次
        // "0 0/1 * * * ?"
        CronScheduleBuilder cron=
                CronScheduleBuilder.cronSchedule("0 0/1 * * * ?");
        return TriggerBuilder.newTrigger()
                .forJob(initJobDetail())
                .withSchedule(cron)
                .withIdentity("initTrigger")
                .build();
    }
    //以下是bloom过滤器的触发信息

 //   @Bean
//               ↓↓↓↓↓
    public JobDetail bloomInitJobDetail(){
        //                              ↓↓↓↓↓
        return JobBuilder.newJob(SeckillBloomInitialJob.class)
                //                 ↓↓↓↓↓
                .withIdentity("bloomInitJobDetail")
                .storeDurably()
                .build();
    }
 //   @Bean
//             ↓↓↓↓↓
    public Trigger bloomInitTrigger(){
        CronScheduleBuilder cron=
                CronScheduleBuilder.cronSchedule("0 0/1 * * * ?");
        return TriggerBuilder.newTrigger()
                //          ↓↓↓↓↓
                .forJob(bloomInitJobDetail())
                //                 ↓↓↓↓↓
                .withIdentity("bloomInitTrigger")
                .withSchedule(cron)
                .build();
    }
}
