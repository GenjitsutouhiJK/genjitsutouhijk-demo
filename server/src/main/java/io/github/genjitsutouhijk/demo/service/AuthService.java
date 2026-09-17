package io.github.genjitsutouhijk.demo.service;

import io.github.genjitsutouhijk.demo.dto.LoginResponse;
import io.github.genjitsutouhijk.demo.exception.BusinessException;
import io.github.genjitsutouhijk.demo.exception.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * 认证相关的业务逻辑
 *
 * Service 层是干什么的？
 *   一句话：Controller 只负责"和 HTTP 打交道"，Service 只负责"业务规则"。
 *   改造之前，这两件事都挤在 AuthController 里。加第二个接口时就麻烦了 ——
 *   比如"注册"也要校验用户名规则，只能把 Controller 里的代码复制一遍。
 *   搬到 Service 之后，规则只有一份，谁都能调。
 *
 * 判断一段代码该放哪一层，问自己一句：
 *   "如果明天不用 HTTP 了（换成定时任务、消息队列、命令行），这行代码还要吗？"
 *     还要   → 业务逻辑，放 Service
 *     不要了 → HTTP 相关，放 Controller
 *   按这个标准，"密码对不对"必须留着，"返回 ApiResponse"就不用留。
 *
 * 为什么不做成 AuthService 接口 + AuthServiceImpl 实现类？
 *   接口的价值在于"一个规范、多种实现"，或者"需要被 Spring 代理/远程调用"。
 *   现在只有一种实现，多写一个接口只是多一次跳转、多一个文件。
 *   等真的出现第二种实现（比如短信登录、第三方登录）时再抽接口也不迟。
 *
 * @Service 的作用：
 *   启动时 Spring 会扫描到这个注解，new 一个 AuthService 对象放进容器，
 *   之后谁需要它，Spring 就负责把同一个对象交给谁（默认单例）。
 */
@Service
public class AuthService {

    // 临时的假用户；里程碑 ② 会换成"查数据库"
    private static final String DEMO_USERNAME = "admin";
    private static final String DEMO_PASSWORD = "123456";

    /**
     * 校验账号密码，通过则签发登录凭证
     *
     * 注意这里的失败方式：**抛异常**，而不是 return 一个"失败对象"。
     * 好处是方法的返回类型干干净净就是"成功时的结果"，
     * 调用方不用先判断 `if (result.success) { ... }` 再取值。
     * 这也就是异常处理器存在的意义 —— 异常从 Service 抛出来，
     * 一路冒泡到 GlobalExceptionHandler，由它统一翻译成 ApiResponse。
     *
     * @param username 已经在 Controller 那层校验过"非空"，这里只管业务规则
     * @param password 同上
     * @return 登录成功后的凭证信息
     * @throws BusinessException 账号或密码不匹配
     */
    public LoginResponse login(String username, String password) {
        // 1. 核对账号密码。
        //    注意写成 常量.equals(变量)：这样 username 为 null 时也不会空指针，
        //    否则变量.equals(常量) 一旦变量是 null 就直接崩了。
        if (!DEMO_USERNAME.equals(username) || !DEMO_PASSWORD.equals(password)) {
            // 提示语刻意模糊成"用户名或密码错误"，不告诉对方到底哪个错了 ——
            // 否则别人可以靠这个提示逐个试出系统里有哪些用户名。
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        // 2. 通过校验，签发凭证。
        //    token 目前还是拼出来的假字符串，里程碑 ③ 换成真 JWT。
        return new LoginResponse(
                "fake-token-for-" + username,
                "Bearer",
                3600L,
                username
        );
    }
}
