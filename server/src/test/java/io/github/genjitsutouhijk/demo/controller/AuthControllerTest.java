package io.github.genjitsutouhijk.demo.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证接口的分支验证
 *
 * 为什么用测试，而不是启动服务再拿 curl 挨个敲？
 *   1. 可重复 —— 每次改代码跑一遍 mvn test 就行，不用手敲十几条命令；
 *   2. 不占端口 —— Spring 以 MOCK 模式启动上下文，请求走内存里的 MockMvc，
 *      不绑定任何端口，所以不会出现"端口被别的程序占了"这种和代码无关的失败；
 *   3. 会一直留着 —— 以后谁不小心把统一异常处理器删了，这里立刻红给你看。
 *
 * 几个类注解的分工：
 *   @SpringBootTest      真启动一整套 Spring 容器（不只是 Controller，Service、
 *                        Repository、数据源、Flyway 全是真的），
 *                        所以这是在验证"从 HTTP 进来到 SQL 出去"的完整链路。
 *                        默认 Web 环境是 MOCK，不会真的监听端口。
 *   @AutoConfigureMockMvc 给测试塞一个 MockMvc 对象，它专门用来"假装发 HTTP 请求"。
 *                        ⚠️ 注意包名：Spring Boot 4 把它挪到了
 *                        org.springframework.boot.webmvc.test.autoconfigure，
 *                        网上 Boot 2/3 的教程写的还是旧的 ...autoconfigure.web.servlet，照抄会编译不过。
 *   @ActiveProfiles("test") 让 Spring 额外读 src/test/resources/application-test.properties，
 *                        把数据源换成**内存**数据库，这样测试不会弄脏你本地的开发数据
 *                        （否则每跑一次测试，users 表里就多几个用户，很快就没法看了）。
 *   @DisplayName         给测试起个中文名，跑出来一眼就知道哪条挂了。
 *
 * 测试里的 admin / 123456 是哪儿来的？
 *   不是常量、不是 mock —— 是 Flyway 的 V2__insert_demo_user.sql 真的往测试库里插进去的。
 *   所以这些测试同时也在验证"迁移脚本能跑通、种子数据是对的"。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("认证接口各分支返回")
class AuthControllerTest {

    private static final String LOGIN_URL = "/api/auth/login";
    private static final String REGISTER_URL = "/api/auth/register";

    @Autowired
    private MockMvc mockMvc;

    // ==================== 登录 ====================

    @Test
    @DisplayName("登录成功：code=0，返回假 token")
    void loginSucceedsWithCorrectCredentials() throws Exception {
        String body = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .contains("fake-token-for-admin")
                .contains("Bearer")
                .contains("\"expiresIn\":3600");
    }

    @Test
    @DisplayName("密码错误：code=1001")
    void loginFailsWithWrongPassword() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrongpass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("用户名根本不存在：也是 code=1001，提示与密码错误完全一致")
    void loginFailsWithUnknownUsername() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"no-such-user\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1001))
                // 这条断言是刻意钉住的：如果哪天有人"好心"把提示改成"该用户不存在"，
                // 测试立刻会红 —— 因为那等于免费告诉攻击者哪些用户名是有效的（用户名枚举）。
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("用户名和密码都是空串：code=1002，两条提示都带上")
    void loginFailsWhenBothFieldsBlank() throws Exception {
        String body = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002))
                .andReturn().getResponse().getContentAsString();

        // 两个字段都不合法，处理器会把提示用"；"拼起来。
        // 不断言顺序，只看两条都在 —— 否则字段顺序一变测试就假失败。
        assertThat(body).contains("用户名不能为空").contains("密码不能为空");
    }

    @Test
    @DisplayName("只传了用户名：code=1002，只提示缺密码")
    void loginFailsWhenPasswordMissing() throws Exception {
        String body = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("密码不能为空").doesNotContain("用户名不能为空");
    }

    @Test
    @DisplayName("请求体只含空格：code=1002（@NotBlank 比 @NotEmpty 严格的地方）")
    void loginFailsWhenFieldsAreWhitespace() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"   \",\"password\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002));
    }

    @Test
    @DisplayName("完全不传 body：code=1003，不是 500 也不是 Spring 默认错误页")
    void loginFailsWhenBodyMissing() throws Exception {
        mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1003))
                .andExpect(jsonPath("$.message").value("请求体格式错误"));
    }

    @Test
    @DisplayName("body 不是合法 JSON：code=1003")
    void loginFailsWhenBodyIsNotJson() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-a-json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1003));
    }

    // ==================== 注册 ====================

    @Test
    @DisplayName("注册成功：code=0，并直接返回凭证（注册即登录）")
    void registerSucceeds() throws Exception {
        String body = mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"alice-pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("fake-token-for-alice").contains("alice");
    }

    @Test
    @DisplayName("刚注册的账号能立刻登录：证明数据真的落库了，不是只返回了个成功")
    void newlyRegisteredUserCanLogIn() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"password\":\"bob-pass\"}"))
                .andExpect(jsonPath("$.code").value(0));

        // 这一条才是真正有价值的断言：注册接口说一句"成功"很容易骗人，
        // 只有"换个入口再查一次，数据确实在了"才说明整条链路是通的。
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\",\"password\":\"bob-pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("bob"));
    }

    @Test
    @DisplayName("注册时用户名已被占用：code=1004")
    void registerFailsWhenUsernameTaken() throws Exception {
        // admin 是 Flyway 的种子用户，一定已经存在
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"whatever\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1004))
                .andExpect(jsonPath("$.message").value("用户名已被占用"));
    }

    @Test
    @DisplayName("注册时用户名太短：code=1002（@Size 生效）")
    void registerFailsWhenUsernameTooShort() throws Exception {
        String body = mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ab\",\"password\":\"good-pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("用户名长度需要在 3 到 20 个字符之间");
    }

    @Test
    @DisplayName("注册时密码太短：code=1002")
    void registerFailsWhenPasswordTooShort() throws Exception {
        String body = mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"charlie\",\"password\":\"123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("密码长度需要在 6 到 32 个字符之间");
    }

    // ==================== 其它 ====================

    @Test
    @DisplayName("访问不存在的路径：保持 HTTP 404，不被兜底处理器吞成 200")
    void unknownPathKeepsNotFoundStatus() throws Exception {
        String body = mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andReturn().getResponse().getContentAsString();

        // 提示里要带上完整路径（含开头斜杠），方便对着地址栏核对
        assertThat(body).contains("/api/does-not-exist");
    }

    @Test
    @DisplayName("存活检查接口照常工作")
    void helloStillWorks() throws Exception {
        mockMvc.perform(get("/hello"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello, Spring Boot!"));
    }
}
