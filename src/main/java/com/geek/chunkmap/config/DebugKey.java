package com.geek.chunkmap.config;

/**
 * 调试模式密码。
 *
 * 编译进 jar，可被反编译读出，因此：
 *   - 只用它来"防止普通玩家误入调试选项"，不是安全边界
 *   - 如果要做真正敏感操作，请另加服务端校验
 *
 * 想改密码：直接把 VALUE 换一个字符串即可。
 */
public final class DebugKey {
    public static final String VALUE = "egg-dev-debug-001790004689356";
    private DebugKey() {}
}