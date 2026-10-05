# 寻光漫游（LuminaWalk）

## 项目定位与学习方式

面向摄影爱好者的垂直服务平台。参考 https://github.com/Qulamb/CityAIHub 的 master 历史，按提交先后逐条分析、实现和验证；每轮处理一条上游提交，完成后再进入下一条。

- 先读取该提交的实际 diff，解释业务目的、关键实现和前置依赖。
- 映射到摄影场景后，说明拟修改的文件、影响和验证方式，得到用户确认后实施。
- 保留上游作者信息，区分继承代码与本项目新增工作。
- 对上游缺陷明确说明，提出修正方案；不要把提交说明等同于已经验证的功能。
- 不擅自跳过提交、提前引入后续组件或升级技术栈。
- 发布和推送需遵循用户当次授权；提交信息使用用户指定内容。

## 当前基线

- 上游仓库：Qulamb/CityAIHub。
- 已分析的初始化提交：`63d6575f8d106e7c2d044db2cad637bb02d28681`（2021-12-22，张虎翼，Initial commit）。
- 此提交属于继承的黑马点评历史；Qulamb 本人最早的提交为 `4c468177e73067be0caec97f529cfa114413295c`，不能混淆作者贡献。
- 本项目已有 `com.luminawalk` 包名、LuminaWalk 项目名和 luminawalk 数据库配置，处于实体及 Controller / Service / Mapper 骨架阶段。
- 后续从上游初始化提交的下一条历史继续；开始前核对远程提交顺序。

## 摄影场景映射

初期保留 Java 类名以便核对上游 diff；涉及字段、表结构或业务范围变化时先讨论。

| 现有模型 | 摄影场景含义 |
| --- | --- |
| User | 摄影爱好者 |
| Shop | 拍摄地点、摄影空间或摄影服务点 |
| ShopType | 拍摄主题分类 |
| Blog | 摄影作品、机位攻略或拍摄笔记 |
| Follow | 关注摄影创作者 |
| BlogComments | 作品或攻略评论 |
| Voucher | 摄影活动券、体验券 |
| VoucherOrder | 活动或摄影服务预约订单 |

## 技术栈与目录

- Maven，Java 8 编译目标，Spring Boot 2.3.12.RELEASE。
- Spring MVC、Spring Data Redis、MyBatis-Plus 3.4.3、MySQL Connector 5.1.47、Lombok。
- Java 代码：`src/main/java/com/luminawalk/`。
- 配置与 Mapper XML：`src/main/resources/`。
- 测试：`src/test/java/com/luminawalk/`。
- 应用端口：8081；MySQL 数据库：luminawalk；Redis 默认端口：6379。

## 启动与验证

标准命令：

```sh
mvn test
mvn spring-boot:run
```

本机 Maven 未加入 PATH 时，可使用 IntelliJ IDEA 自带 Maven：

```sh
"/Applications/IntelliJ IDEA.app/Contents/plugins/maven-plugin/lib/maven3/bin/mvn" test
"/Applications/IntelliJ IDEA.app/Contents/plugins/maven-plugin/lib/maven3/bin/mvn" spring-boot:run
```

当前 `contextLoads` 只验证 Spring 上下文加载，不证明 MySQL / Redis 实际读写或业务接口可用。后续按各条提交的业务风险增加相应验证，执行前检查运行时 JDK 和依赖是否兼容。

提交前检查 diff 与 Git 暂存列表，排除 `target/`、IDE 配置和 `.DS_Store`，不提交真实凭据或本地敏感数据。
