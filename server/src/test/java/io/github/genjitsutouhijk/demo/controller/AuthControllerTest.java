package io.github.genjitsutouhijk.demo.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登录接口的分支验证
 *
 * 为什么用测试，而不是启动服务再拿 curl 挨个敲？
 *   1. 可重复 —— 每次改代码跑一遍 mvn test 就行，不用手敲 8 条命令；
 *   2. 不占端口 —— Spring 以 MOCK 模式启动上下文，请求走内存里的 MockMvc，
 *      不绑定任何端口，所以不会出现"端口被别的程序占了"这种和代码无关的失败；
 *   3. 会一直留着 —— 以后谁不小心把统一异常处理器删了，这里立刻红给你看。
 *
 * 三个注解的分工：
 *   @SpringBootTest      真启动一整套 Spring 容器（不只是 Controller，Service 也是真的），
 *                        所以这是在验证"从 HTTP 进来到 JSON 出去"的完整链路。
 *                        默认 Web 环境是 MOCK，不会真的监听端口。
 *   @AutoConfigureMockMvc 给测试塞一个 MockMvc 对象，它专门用来"假装发 HTTP 请求"。
 *                        ⚠️ 注意包名：Spring Boot 4 把它挪到了
 *                        org.springframework.boot.webmvc.test.autoconfigure，
 *                        网上 Boot 2/3 的教程写的还是旧的 ...autoconfigure.web.servlet，照抄会编译不过。
 *   @DisplayName         给测试起个中文名，跑出来一眼就知道哪条挂了。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("POST /api/auth/login 各分支返回")
class AuthControllerTest {

    private static final String LOGIN_URL = "/api/auth/login";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("账号密码正确：code=0，并返回假 token")
    void loginSucceedsWithCorrectCredentials() throws Exception {
        String body = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();

        // 顺带确认 data 里的字段确实都拼出来了
        assertThat(body)
                .contains("fake-token-for-admin")
                .contains("Bearer")
                .contains("\"expiresIn\":3600");
    }

    @Test
    @DisplayName("密码错误：code=1001，由 BusinessException 抛出")
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
