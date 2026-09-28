package edu.whut.clf.post.model;

import lombok.Data;

/** post_images 表实体（仅公开图片）。 */
@Data
public class PostImage {
    private Long id;
    private Long postId;
    private Long fileId;
    private Integer sortOrder;
}
