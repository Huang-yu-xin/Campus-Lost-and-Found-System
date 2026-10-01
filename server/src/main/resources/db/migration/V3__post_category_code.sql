-- V3 (P4)：类别归一化——category_code 两级字典编码 + 同类目候选臂索引。
-- 回填为"精确别名集合"映射，镜像 matching/category-dictionary.v1.txt 的别名表；
-- 未命中的行保持 NULL，运行期由 CategoryDictionary 在写入时归一，读取时回退原文精确比对。

ALTER TABLE posts ADD COLUMN category_code VARCHAR(48) NULL;
ALTER TABLE posts ADD INDEX idx_post_match_cat (type, status, category_code, event_time);

UPDATE posts SET category_code = 'umbrella'        WHERE category_code IS NULL AND category IN ('雨伞','长柄伞','晴雨伞','自动伞','折叠伞','伞','黑色长柄伞','折叠晴雨伞','透明自动伞');
UPDATE posts SET category_code = 'earphones'       WHERE category_code IS NULL AND category IN ('耳机','蓝牙耳机','无线耳机','有线耳机','耳麦');
UPDATE posts SET category_code = 'phone'           WHERE category_code IS NULL AND category IN ('手机','电话','智能机','安卓手机','黑色智能手机','旧款安卓手机');
UPDATE posts SET category_code = 'powerbank'       WHERE category_code IS NULL AND category IN ('充电宝','移动电源','白色充电宝','绿色迷你充电宝');
UPDATE posts SET category_code = 'laptop'          WHERE category_code IS NULL AND category IN ('笔记本电脑','笔记本','手提电脑','电脑','银色笔记本电脑','游戏本');
UPDATE posts SET category_code = 'campus-card'     WHERE category_code IS NULL AND category IN ('校园卡','饭卡','学生卡','一卡通','餐卡','蓝色卡套的校园卡','皮夹里的校园卡');
UPDATE posts SET category_code = 'id-docs'         WHERE category_code IS NULL AND category IN ('证件','身份证','驾驶证','护照','医保卡','学生证');
UPDATE posts SET category_code = 'keys'            WHERE category_code IS NULL AND category IN ('钥匙','一串钥匙','宿舍钥匙','电动车钥匙','钥匙串','锁匙','小熊挂件钥匙');
UPDATE posts SET category_code = 'cup'             WHERE category_code IS NULL AND category IN ('水杯','保温杯','杯子','茶杯','吸管杯','塑料杯','马克杯','白色保温杯','透明塑料水杯','粉色吸管杯');
UPDATE posts SET category_code = 'wallet'          WHERE category_code IS NULL AND category IN ('钱包','卡包','皮夹','黑色短款钱包','棕色长款钱包','帆布卡包');
UPDATE posts SET category_code = 'books'           WHERE category_code IS NULL AND category IN ('书本教材','教材','课本','书本','真题','辅导书','高数教材','考研英语真题','一套专业课本');
UPDATE posts SET category_code = 'watch-accessory' WHERE category_code IS NULL AND category IN ('手表饰品','手表','手链','项链','戒指','耳环','电子手表','银色手链','黑色皮带手表');
UPDATE posts SET category_code = 'clothing'        WHERE category_code IS NULL AND category IN ('衣物','衣服','卫衣','围巾','帽子','棒球帽','外套','手套','黑色卫衣','灰色围巾','蓝色棒球帽');
UPDATE posts SET category_code = 'glasses'         WHERE category_code IS NULL AND category IN ('眼镜','墨镜','隐形眼镜','太阳镜','黑框眼镜','隐形眼镜盒');
UPDATE posts SET category_code = 'other'           WHERE category_code IS NULL AND category IN ('其他','杂物','保温饭盒','乒乓球拍','跳绳','水壶');
