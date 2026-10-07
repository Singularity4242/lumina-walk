-- Based on FD11542
-- 建表 tb_user、tb_user_info、tb_shop_type、tb_shop、tb_blog、
-- tb_blog_comments、tb_follow、tb_voucher、tb_voucher_order
-- 运行要求：MySQL 8.0.16及以上，并启用严格SQL模式，确保CHECK与类型范围校验生效。
-- UNSIGNED整数写入时仍须遵守Java Long/Integer可表示的范围。
SET NAMES utf8mb4;

-- 1. 用户基础信息
CREATE TABLE tb_user (
                         id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                         phone CHAR(11) NOT NULL COMMENT '大陆手机号，唯一登录账号',
                         password VARCHAR(255) NULL COMMENT '密码哈希；验证码账号可为空',
                         nick_name VARCHAR(64) NOT NULL COMMENT '用户昵称',
                         icon VARCHAR(512) NOT NULL DEFAULT '' COMMENT '头像图片路径',
                         create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                         update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
                         PRIMARY KEY (id),
                         UNIQUE KEY uk_phone (phone),
                         CONSTRAINT ck_user_phone CHECK (CHAR_LENGTH(TRIM(phone)) > 0),
                         CONSTRAINT ck_user_nickname CHECK (CHAR_LENGTH(TRIM(nick_name)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户基础信息表';

-- 2. 用户详细资料
CREATE TABLE tb_user_info (
                              user_id BIGINT UNSIGNED NOT NULL COMMENT '使用tb_user.id，不独立自增',
                              city VARCHAR(64) NOT NULL DEFAULT '' COMMENT '所在城市',
                              introduce VARCHAR(128) NOT NULL DEFAULT '' COMMENT '个人简介',
                              fans INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '粉丝数，由业务代码维护',
                              followee INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '关注人数，由业务代码维护',
                              gender TINYINT UNSIGNED NULL COMMENT '0男，1女，NULL未填写',
                              birthday DATE NULL COMMENT '生日，未填写为NULL',
                              credits INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '用户积分',
                              level TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '用户等级，范围0至9',
                              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
                              PRIMARY KEY (user_id),
                              CONSTRAINT ck_user_info_counts CHECK (fans >= 0 AND followee >= 0 AND credits >= 0),
                              CONSTRAINT ck_user_info_gender CHECK (gender IN (0, 1)),
                              CONSTRAINT ck_user_info_level CHECK (level BETWEEN 0 AND 9)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户详细资料表';

-- 3. 地点分类
CREATE TABLE tb_shop_type (
                              id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                              name VARCHAR(64) NOT NULL COMMENT '拍摄地点或服务分类名称',
                              icon VARCHAR(512) NOT NULL DEFAULT '' COMMENT '分类图标路径',
                              sort INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '展示顺序，数值越小越靠前',
                              create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                              update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
                              PRIMARY KEY (id),
                              KEY idx_sort (sort),
                              CONSTRAINT ck_shop_type_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
                              CONSTRAINT ck_shop_type_sort CHECK (sort >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='地点分类表';

-- 4. 具体地点
CREATE TABLE tb_shop (
                         id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                         name VARCHAR(128) NOT NULL COMMENT '拍摄地点或摄影服务点名称',
                         type_id BIGINT UNSIGNED NOT NULL COMMENT '分类ID，逻辑关联tb_shop_type.id',
                         images VARCHAR(4096) NOT NULL DEFAULT '' COMMENT '图片路径，逗号分隔',
                         area VARCHAR(128) NOT NULL DEFAULT '' COMMENT '所属区域或商圈',
                         address VARCHAR(255) NOT NULL COMMENT '详细地址',
                         x DECIMAL(10, 7) NOT NULL COMMENT '经度，单位为度，范围-180至180',
                         y DECIMAL(10, 7) NOT NULL COMMENT '纬度，单位为度，范围-90至90',
                         avg_price INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '平均价格，单位为元',
                         sold INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已售数量',
                         comments INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '评论数量',
                         score INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0未评分；10至50表示1至5分',
                         open_hours VARCHAR(128) NOT NULL DEFAULT '' COMMENT '开放或营业时间，例如10:00-22:00',
                         create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                         update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
                         PRIMARY KEY (id),
                         KEY idx_type_id (type_id),
                         CONSTRAINT ck_shop_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
                         CONSTRAINT ck_shop_address CHECK (CHAR_LENGTH(TRIM(address)) > 0),
                         CONSTRAINT ck_shop_coordinates CHECK (x BETWEEN -180 AND 180 AND y BETWEEN -90 AND 90),
                         CONSTRAINT ck_shop_counts CHECK (avg_price >= 0 AND sold >= 0 AND comments >= 0),
                         CONSTRAINT ck_shop_score CHECK (score = 0 OR score BETWEEN 10 AND 50)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='具体地点表';

-- 5. 笔记表
CREATE TABLE tb_blog (
                         id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                         shop_id BIGINT UNSIGNED NOT NULL COMMENT '地点ID，逻辑关联tb_shop.id',
                         user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID，逻辑关联tb_user.id',
                         title VARCHAR(255) NOT NULL COMMENT '摄影作品或笔记标题',
                         images VARCHAR(4096) NOT NULL DEFAULT '' COMMENT '作品图片路径，逗号分隔；最多9张由应用层校验',
                         content TEXT NOT NULL COMMENT '摄影作品或笔记正文',
                         liked INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '点赞数，由业务代码维护',
                         comments INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '评论数量，由业务代码维护',
                         create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                         update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
                         PRIMARY KEY (id),
                         KEY idx_user_id_create_time (user_id, create_time),
                         KEY idx_shop_id_create_time (shop_id, create_time),
                         CONSTRAINT ck_blog_title CHECK (CHAR_LENGTH(TRIM(title)) > 0),
                         CONSTRAINT ck_blog_counts CHECK (liked >= 0 AND comments >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记表';

-- 6. 笔记评论表
CREATE TABLE tb_blog_comments (
                                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                  user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID，逻辑关联tb_user.id',
                                  blog_id BIGINT UNSIGNED NOT NULL COMMENT '作品或笔记ID，逻辑关联tb_blog.id',
                                  parent_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '一级评论为0；回复填一级评论ID',
                                  answer_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '本方案以0表示没有回复目标',
                                  content VARCHAR(1024) NOT NULL COMMENT '评论正文',
                                  liked INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '评论点赞数，由业务代码维护',
                                  status TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0正常，1被举报，2禁止查看',
                                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
                                  PRIMARY KEY (id),
                                  KEY idx_blog_id_parent_id_create_time (blog_id, parent_id, create_time),
                                  KEY idx_user_id (user_id),
                                  KEY idx_parent_id (parent_id),
                                  KEY idx_answer_id (answer_id),
                                  CONSTRAINT ck_comment_refs CHECK (parent_id >= 0 AND answer_id >= 0),
                                  CONSTRAINT ck_comment_content CHECK (CHAR_LENGTH(TRIM(content)) > 0),
                                  CONSTRAINT ck_comment_liked CHECK (liked >= 0),
                                  CONSTRAINT ck_comment_status CHECK (status IN (0, 1, 2))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记评论表';
-- 7. 用户关注
CREATE TABLE tb_follow (
                           id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                           user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID，逻辑关联tb_user.id',
                           follow_user_id BIGINT UNSIGNED NOT NULL COMMENT '被关注用户ID，逻辑关联tb_user.id',
                           create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
                           PRIMARY KEY (id),
                           UNIQUE KEY uk_user_id_follow_user_id (user_id, follow_user_id), -- 阻止同一用户重复关注同一个人
                           KEY idx_follow_user_id_user_id (follow_user_id, user_id),
                           CONSTRAINT ck_follow_not_self CHECK (user_id <> follow_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户关注关系表';

-- 8. 活动券 / 体验券
-- 此版本的库存和有效期直接放在tb_voucher中
CREATE TABLE tb_voucher (
                            id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                            shop_id BIGINT UNSIGNED NOT NULL COMMENT '地点ID，逻辑关联tb_shop.id',
                            title VARCHAR(128) NOT NULL COMMENT '活动券或体验券标题',
                            sub_title VARCHAR(255) NOT NULL DEFAULT '' COMMENT '副标题',
                            rules VARCHAR(2048) NOT NULL DEFAULT '' COMMENT '使用规则',
                            pay_value BIGINT UNSIGNED NOT NULL COMMENT '支付金额，单位为分；9900表示99元',
                            actual_value BIGINT UNSIGNED NOT NULL COMMENT '抵扣金额，单位为分；10000表示100元',
                            stock INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '剩余库存，由业务代码维护',
                            create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                            begin_time DATETIME NOT NULL COMMENT '有效期开始时间',
                            end_time DATETIME NOT NULL COMMENT '有效期结束时间',
                            update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
                            PRIMARY KEY (id),
                            KEY idx_shop_id (shop_id),
                            CONSTRAINT ck_voucher_title CHECK (CHAR_LENGTH(TRIM(title)) > 0),
                            CONSTRAINT ck_voucher_values CHECK (pay_value >= 0 AND actual_value > 0 AND stock >= 0),
                            CONSTRAINT ck_voucher_period CHECK (end_time > begin_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动券与体验券表';

-- 9. 券订单
CREATE TABLE tb_voucher_order (
                                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                  user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID，逻辑关联tb_user.id',
                                  voucher_id BIGINT UNSIGNED NOT NULL COMMENT '活动券ID，逻辑关联tb_voucher.id',
                                  pay_type TINYINT UNSIGNED NULL COMMENT '1余额，2支付宝，3微信；未选择时NULL',
                                  status TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1未支付，2已支付，3已核销，4已取消，5退款中，6已退款',
                                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  pay_time DATETIME NULL COMMENT '支付时间，未支付为NULL',
                                  use_time DATETIME NULL COMMENT '核销时间，未核销为NULL',
                                  refund_time DATETIME NULL COMMENT '退款完成时间，未退款为NULL',
                                  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
                                  PRIMARY KEY (id),
                                  KEY idx_user_id_create_time (user_id, create_time),
                                  KEY idx_voucher_id_user_id (voucher_id, user_id),
                                  CONSTRAINT ck_order_pay_type CHECK (pay_type IN (1, 2, 3)),
                                  CONSTRAINT ck_order_status CHECK (status BETWEEN 1 AND 6),
                                  CONSTRAINT ck_order_pay_time CHECK (pay_time IS NULL OR pay_time >= create_time),
                                  CONSTRAINT ck_order_use_time CHECK (use_time IS NULL OR (pay_time IS NOT NULL AND use_time >= pay_time)),
                                  CONSTRAINT ck_order_refund_time CHECK (refund_time IS NULL OR (pay_time IS NOT NULL AND refund_time >= pay_time))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='摄影活动券与体验券订单表';
