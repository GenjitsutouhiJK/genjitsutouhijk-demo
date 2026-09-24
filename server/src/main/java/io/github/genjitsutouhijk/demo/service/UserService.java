package io.github.genjitsutouhijk.demo.service;

import io.github.genjitsutouhijk.demo.dto.UserProfile;
import io.github.genjitsutouhijk.demo.entity.User;
import io.github.genjitsutouhijk.demo.exception.BusinessException;
import io.github.genjitsutouhijk.demo.exception.ErrorCode;
import io.github.genjitsutouhijk.demo.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户资料相关的业务逻辑
 *
 * 为什么它和 AuthService 分开，而不是塞进一个 UserService 里全管了？
 *   两者管的**东西**不同：
 *     AuthService —— 登录、注册、签发凭证。它关心的是"你是谁、能不能进来"。
 *     UserService —— 已有用户的资料。它关心的是"进来之后能看/改什么"。
 *   将来用户模块会长出改密码、改昵称、上传头像……全塞进 AuthService 会让那个类越来越难读。
 *   按"业务能力"切分，而不是按"都跟 user 表有关"切分。
 *
 * 那为什么不做成 UserService 接口 + UserServiceImpl？
 *   和 AuthService 一样的理由：只有一种实现，抽接口只是多一次跳转。见 AuthService 的注释。
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 按用户名取资料
     *
     * @param username 来自 JWT 里的 subject —— 注意它**不是**前端传上来的参数。
     *                 这一点很关键：如果写成"前端传 userId 我就返回谁的资料"，
     *                 那用户只要把 id 改成别人的，就能看到别人的数据（这叫"越权访问"）。
     *                 从凭证里取身份，是杜绝这类问题的根本办法。
     *                 所以接口路径叫 /api/user/me（我），而不是 /api/user/{id}。
     *
     * @throws BusinessException 凭证是有效的，但对应账号已经被删掉了。
     *                           这种"票真、人没了"的情况很少见，但确实会发生
     *                           （比如管理员删号，而用户的 token 还没到期）。
     *                           这里判成 1005 让前端回登录页，而不是抛 500 ——
     *                           对用户来说，"你重新登一次"永远是合理的补救动作。
     */
    @Transactional(readOnly = true)
    public UserProfile getProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.UNAUTHORIZED, "账号已不存在，请重新登录"));

        return new UserProfile(user.getId(), user.getUsername(), user.getCreatedAt());
    }
}
