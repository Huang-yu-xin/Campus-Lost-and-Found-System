package edu.whut.clf.file;

import edu.whut.clf.file.model.StoredFile;

/**
 * 私密文件访问检查扩展点。各模块（认领/争议/线索）实现自己的判定，
 * 避免 FileService 反向依赖业务模块，同时保证私密证据逐记录鉴权。
 */
public interface FilePrivateAccessChecker {

    /** 该 checker 是否负责此用途。 */
    boolean supports(String purpose);

    /** 指定用户是否有权访问该私密文件。 */
    boolean canAccess(Long userId, StoredFile file);
}
