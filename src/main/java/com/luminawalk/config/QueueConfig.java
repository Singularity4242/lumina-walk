package com.luminawalk.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;

@Configuration
public class QueueConfig {
    // 普通交换机名称
    public static final String COMMON_EXCHANGE = "Common";
    // 死信交换机名称
    public static final String DEAD_DEAD_LETTER_EXCHANGE = "Dead-letter";
    // 普通队列名称
    public static final String QUEUE_C = "CQ";
    // 死信队列名称
    public static final String DEAD_LETTER_QUEUE_D = "DLQ";

    /**
     * 声明普通交换机
     *
     * @return DirectExchange
     */
    @Bean("commonExchange")
    public DirectExchange commonExchange(){
        return new DirectExchange(COMMON_EXCHANGE);
    }

    /**
     * 声明死信交换机
     *
     * @return DirectExchange
     */
    @Bean("deadLetterExchange")
    public DirectExchange deadLetterExchange(){
        return new DirectExchange(DEAD_DEAD_LETTER_EXCHANGE);
    }

    /**
     * 声明普通队列C, 并绑定死信交换机及设置消息TTL
     *
     * 设置说明：
     * - x-dead-letter-exchange: 配置消息过期后转发的死信交换机名称
     * - x-dead-letter-routing-key: 配置转发到死信交换机时使用的路由键，此处与死信队列绑定时的路由键一致（"DLQ"）
     * - x-message-ttl: 消息存活时间（此处设置为10000毫秒，即10秒）
     *
     * @return Queue
     */
    @Bean("queueC")
    public Queue queueC(){
        HashMap<String, Object> arguments = new HashMap<>();
        // 消息在队列中存活10秒后失效，进入死信队列
        arguments.put("x-message-ttl", 10000);
        // 配置死信交换机
        arguments.put("x-dead-letter-exchange", DEAD_DEAD_LETTER_EXCHANGE);
        // 配置死信路由键，绑定到死信队列时使用
        arguments.put("x-dead-letter-routing-key", "DLQ");

        return QueueBuilder.durable(QUEUE_C)
                .withArguments(arguments)
                .build();
    }

    /**
     * 声明死信队列D
     *
     * @return Queue
     */
    @Bean("deadLetterQueueD")
    public Queue deadLetterQueueD(){
        return QueueBuilder.durable(DEAD_LETTER_QUEUE_D)
                .build();
    }

    /**
     * 普通队列C与普通交换机Common绑定
     *
     * 当消息发送到交换机Common，并使用路由键 "CQ" 时，
     * 消息将被路由到队列CQ。
     *
     * @param queueC 普通队列
     * @param commonExchange 普通交换机
     * @return Binding
     */
    @Bean
    public Binding bindingQueueCToCommonExchange(@Qualifier("queueC") Queue queueC,
                                                 @Qualifier("commonExchange") DirectExchange commonExchange) {
        return BindingBuilder.bind(queueC).to(commonExchange).with("CQ");
    }

    /**
     * 死信队列D与死信交换机Dead-letter绑定
     *
     * 当普通队列CQ中的消息由于TTL过期或其他原因被转为死信后，
     * 消息会转发到死信交换机Dead-letter，并使用路由键 "DLQ"，
     * 从而被路由到死信队列DLQ。
     *
     * @param deadLetterQueueD 死信队列
     * @param deadLetterExchange 死信交换机
     * @return Binding
     */
    @Bean
    public Binding bindingDeadLetterQueueDToDeadLetterExchange(@Qualifier("deadLetterQueueD") Queue deadLetterQueueD,
                                                               @Qualifier("deadLetterExchange") DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueueD).to(deadLetterExchange).with("DLQ");
    }
}
