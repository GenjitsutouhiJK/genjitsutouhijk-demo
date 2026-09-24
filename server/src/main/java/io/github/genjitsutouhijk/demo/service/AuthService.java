package io.github.genjitsutouhijk.demo.service;

import io.github.genjitsutouhijk.demo.dto.LoginResponse;
import io.github.genjitsutouhijk.demo.entity.User;
import io.github.genjitsutouhijk.demo.exception.BusinessException;
import io.github.genjitsutouhijk.demo.exception.ErrorCode;
import io.github.genjitsutouhijk.demo.repository.UserRepository;
import io.github.genjitsutouhijk.demo.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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
 * 关于依赖注入：构造器里要的 UserRepository 和 PasswordEncoder，
 *   都是别人（Spring Data / PasswordConfig）造好放进容器的，这里只管要、不管造。
 *   这叫"控制反转"——对象的创建权交出去了。好处是这一层完全不知道数据存在哪、
 *   密码用什么算法，只知道"我要一个能查用户的、一个能比密码的"，
 *   所以将来换数据库、换加密算法，这个文件都不用改。
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * 登录：核对账号密码，通过则签发登录凭证
     *
     * 注意这里的失败方式：**抛异常**，而不是 return 一个"失败对象"。
     * 好处是方法的返回类型干干净净就是"成功时的结果"，
     * 调用方不用先判断 `if (result.success) { ... }` 再取值。
     * 这也就是异常处理器存在的意义 —— 异常从 Service 抛出来，
     * 一路冒泡到 GlobalExceptionHandler，由它统一翻译成 ApiResponse。
     *
     * @Transactional(readOnly = true) 是什么意思？
     *   它把整个方法包在一段数据库事务里，并向数据库声明"我只读不写"。
     *   好处有两个：一是方法内多条查询看到的是同一个一致的时间点；
     *   二是数据库知道这次不写，可以做些优化（H2 上影响不大，MySQL/PostgreSQL 上比较明显）。
     *   日常约定：查询方法都加 readOnly = true，写方法不加。
     *
     * @param username 已经在 Controller 那层校验过"非空"，这里只管业务规则
     * @param password 用户输入的**明文**密码，拿来和库里的哈希比对
     * @return 登录成功后的凭证信息
     * @throws BusinessException 账号不存在或密码不匹配
     */
    @Transactional(readOnly = true)
    public LoginResponse login(String username, String password) {

        // 1. 按用户名把用户查出来，查不到就直接按"登录失败"处理。
        //
        //    ⚠️ 注意这里**没有**单独抛一个"用户不存在"的错误码，和下面密码错误用的是同一个。
        //       这是故意的：如果分开提示，攻击者就能拿一堆用户名来试，
        //       根据提示区分出"这个用户名存在"和"不存在"，
        //       等于免费帮他把系统里有哪些账号列了出来。这种攻击叫"用户名枚举"。
        //       统一提示"用户名或密码错误"，他就什么都推断不出来。
        //
        //    orElseThrow(Supplier) 的用法：Optional 里有值就取出来，
        //    是空的就执行括号里的代码（这里是抛异常）。
        //    注意写成 () -> new BusinessException(...) 这种"延迟执行"的形式，
        //    意思是"真查不到时再造这个异常对象"，不然每次登录都要白造一个用不上的对象。
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));

        // 2. 比对密码。
        //    ★ 绝对不能写成 !password.equals(user.getPasswordHash()) ——
        //      库里存的是哈希，明文和哈希永远不可能相等，那样写谁都登不上。
        //      matches(明文, 库里的哈希) 会把明文用同样的方式算一遍
        //      （并取出哈希里附带的那个随机盐），再比较结果。
        //      因为 BCrypt 是故意设计得慢的，这一步大约要花 100 毫秒。
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        // 3. 两项都对上了，签发凭证。
        return issueToken(user.getUsername());
    }

    /**
     * 注册：创建一个新用户，并直接返回登录凭证（也就是"注册即登录"）
     *
     * 这个方法和 login 的差别，正好体现了"分层"的价值：
     * 它没有复制粘贴 login 的任何代码，只是换了不同的前置检查。
     *
     * @Transactional（不带 readOnly）表示"这个方法会写数据库"。
     *   加上它的意义是**原子性**：一旦方法中途抛异常，
     *   前面已经做的写操作会整体回滚，不会留下半成品数据。
     *   现在这个方法里只有一次写操作，还看不出效果；
     *   等以后"注册要同时建用户 + 发一条欢迎消息"时，它的作用就体现出来了。
     *
     * @throws BusinessException 用户名已被占用
     */
    @Transactional
    public LoginResponse register(String username, String password) {

        // 1. 先查一下这个用户名是不是已经有人用了。
        //    这一步只是"提前给个友好提示"，它并不能 100% 挡住重复 ——
        //    详见 GlobalExceptionHandler 里 handleDataIntegrityViolation 的说明。
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.USERNAME_TAKEN);
        }

        // 2. ★ 关键一步：存进去的必须是**加密后**的密码，绝不能用用户传进来的明文。
        //    encode() 每次调用结果都不一样（因为加了随机盐），这是正常现象，不是 bug。
        //
        //    时间戳由这里给出，而不是交给数据库的 CURRENT_TIMESTAMP，
        //    好处是"这一行是什么时候建的"完全在 Java 代码里可见、可控、可测试。
        User user = new User(username, passwordEncoder.encode(password), LocalDateTime.now());

        // 3. save() 就是 INSERT。因为 id 是空的，Spring Data 判定这是"新增"而不是"更新"。
        //    执行完之后，数据库生成的自增 id 会被回填进返回的对象里。
        User saved = userRepository.save(user);

        // 4. 注册成功直接发凭证，前端不用再调一次登录接口。
        return issueToken(saved.getUsername());
    }

    /**
     * 签发登录凭证
     *
     * 抽成私有方法，是因为 login 和 register 都要用它 ——
     * 这两处必须产出完全一样的东西，否则很容易出现"注册给的 token 格式和登录不一样"这种难查的问题。
     * 规则只有一份，以后要改就改这一个地方。
     *
     * ============ 里程碑 ③ 的改动就在这一行 ============
     *
     * 之前返回的是拼出来的字符串 "fake-token-for-" + username：
     *   - 后端既不记录也不校验，前端拿着它回来等于白拿；
     *   - 而且谁都能自己拼一个，冒充任意用户名。
     *
     * 现在交给 JwtService 用密钥签名，它会产出一串 xxx.yyy.zzz 三段式字符串：
     *   - 里面有用户名和过期时间，由服务端**签名**保护，改一个字就验不过；
     *   - 服务端不需要存任何东西，收到后自己验签就能认出"这是谁"。
     *
     * 有效期也从 JwtService 里取，而不是在这里硬编码 3600 ——
     * 这样"响应里告诉前端的有效期"和"token 里真正写进去的过期时间"
     * 一定来自同一个配置项。否则改了一处忘一处，
     * 会出现"前端以为还有 1 小时，实际 10 分钟就失效了"这种诡异现象。
     */
    private LoginResponse issueToken(String username) {
        return new LoginResponse(
                jwtService.issue(username),
                "Bearer",
                jwtService.getExpiresInSeconds(),
                username
        );
    }
}
