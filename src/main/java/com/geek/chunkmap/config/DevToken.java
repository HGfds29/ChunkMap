package com.geek.chunkmap.config;

/**
 * 编译时硬编码的 GitHub Token。
 *
 * ⚠ 注意：jar 可被反编译，这里的字符串常量能被任何拿到 jar 的人读出。
 * 因此该 token 必须：
 *   - 权限最小（只需 public_repo，或 fine-grained 里只勾 Issues: Read and Write）
 *   - 定期轮换（建议每天或每周在 github.com/settings/tokens 重建一次）
 *   - 一旦发现有滥用迹象，立即撤销
 *
 * 读取优先级（FeedbackScreen.currentToken）：
 *   1. config/chunkmap.json 的 githubToken 字段（用户在配置界面填的，覆盖此默认值）
 *   2. DevToken.VALUE（本文件）
 */
public final class DevToken {
    /** 把下面引号里的内容替换生成的 token。 */
    public static final String VALUE = "";

    private DevToken() {}
}